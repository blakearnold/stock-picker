package com.theblakearnold.stocksolver;

import com.google.common.base.Optional;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPObjective;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPSolver.OptimizationProblemType;
import com.google.ortools.linearsolver.MPVariable;

import com.theblakearnold.stocksolver.model.AccountModel;
import com.theblakearnold.stocksolver.model.CategoryGroupModel;
import com.theblakearnold.stocksolver.model.CategoryModel;
import com.theblakearnold.stocksolver.model.StockHoldingModel;
import com.theblakearnold.stocksolver.model.StockModel;
import com.theblakearnold.stocksolver.storage.StockSolverStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.inject.Inject;

/**
 * Linear programming example that shows how to use the API.
 */

public class StockSolver {

  private final StockSolverStorage stockSolverStorage;

  private final static Logger log = Logger.getLogger(StockSolver.class.getName());

  @Inject
  public StockSolver(StockSolverStorage stockSolverStorage) {
    this.stockSolverStorage = stockSolverStorage;
  }

  static {
    System.loadLibrary("jniortools");
  }

  private static MPSolver createSolver(OptimizationProblemType solverType) {
    return new MPSolver("IntegerProgrammingExample", solverType);
  }

  public void printCurrentPercentage() {
    printPercentage(stockSolverStorage.getAccounts());
  }

  private void printPercentage(List<AccountModel> accounts) {
    double totalCash = 0;
    for (AccountModel accountModel : accounts) {
      // Sum up all money
      totalCash += accountModel.value();
    }
    for (CategoryGroupModel categoryGroupModel : stockSolverStorage.getCategoryGroups()) {
      double categoryGroupTargetPercentage = 0;
      double categoryGroupTarget = 0;
      double categoryGroupActual = 0;
      log.info("---Category Group---" + categoryGroupModel.name());
      for (CategoryModel category : categoryGroupModel.categories()) {
        double categoryTarget = category.percent() / 100.0 * totalCash;
        categoryGroupTarget += categoryTarget;
        categoryGroupTargetPercentage += category.percent();
        double actual = 0;

        for (AccountModel account : accounts) {
          for (StockHoldingModel stockHolding : account.stocks()) {
            if (stockHolding.stockModel().hasCategoryAllocation(category.name())) {
              // setCoefficient of the percent the stock is in the category
              double percent = stockHolding.stockModel().percentage(category.name());
              actual += stockHolding.currentHolding() * (percent / 100.0);
            }
          }
        }
        categoryGroupActual += actual;
        log.info(String.format("%s: Target %s, %s, Actual %s, %s", category,
                                         categoryTarget, category.percent(), actual,
                                         actual / totalCash * 100));
      }
      log.info(String.format("--- GROUP Totals: Target %s, %s, Actual %s, %s",
                                       categoryGroupTarget, categoryGroupTargetPercentage,
                                       categoryGroupActual,
                                       categoryGroupActual / totalCash * 100));
    }
    for (AccountModel account : accounts) {
      log.info(String.format("%s: Cash Invested: %s", account.name(), account.value()));
    }
    log.info(String.format("Total Cash Invested: %s", totalCash));
  }

  public void optimizeWiggleRoom(OptimizationProblemType solverType, double optimizeTil) {
    double lowerBound = 0;
    double upperBound = 100;
    Optional<List<AccountModel>> lastSolution = Optional.absent();
    double lastGoodPercent = 0;
    int count = 0;
    // Run until we hit the optimize percent, or the last run was not successful.
    while (Math.abs(lowerBound - upperBound) > optimizeTil) {
      count++;
      double wigglePercent = (lowerBound + upperBound) / 2;
      log.fine("Trying wiggle room at " + wigglePercent + "%");
      Optional<List<AccountModel>> solution =
          runSolver(solverType, wigglePercent);
      if (solution.isPresent()) {
        upperBound = wigglePercent;
        lastSolution = solution;
        lastGoodPercent = wigglePercent;
      } else {
        lowerBound = wigglePercent;
      }
    }
    if (!lastSolution.isPresent()) {
      log.warning("Failed to optimize wiggle percent.");
    } else {
      log.info(String.format("Optimized in %s tries, with percent %s.",
                                       count, lastGoodPercent));
      printDiff(stockSolverStorage.getAccounts(), lastSolution.get());
      printPercentage(lastSolution.get());
    }
  }

  public Optional<List<AccountModel>> runSolver(
      MPSolver.OptimizationProblemType solverType, double categoryWiggleRoom) {
    MPSolver solver = createSolver(solverType);
    if (solver == null) {
      throw new IllegalArgumentException("Could not create solver " + solverType);
    }
    double infinity = MPSolver.infinity();
    Map<AccountModel, Map<StockModel, MPVariable>> mpVariables = new HashMap<>();
    List<MPConstraint> otherConstraints = new ArrayList<>();
    double totalCash = 0;

    // Initialize variables, one per stock.
    // Also count the amount of total cash in all accounts for later use.
    for (AccountModel accountModel : stockSolverStorage.getAccounts()) {
      // Sum up all money
      totalCash += accountModel.value();
      Map<StockModel, MPVariable> stockModelVariables = new HashMap<>();
      for (StockHoldingModel stockHoldingModel : accountModel.stocks()) {
        // Start name with '_' to ensure it doesn't start with a number, which is not accepted for
        // cp solver.
        String name = "_" + accountModel.name() + "_" + stockHoldingModel.stockModel().ticker();
        // Set stock holdings to be account minimum holdings to infinity.
        MPVariable mpVariable;
        if (!stockHoldingModel.isLocked()) {
          mpVariable = solver.makeNumVar(stockHoldingModel.minimumBalance(), infinity, name);
        } else {
          mpVariable = solver.makeNumVar(stockHoldingModel.minimumBalance(),
                                         stockHoldingModel.minimumBalance(), name);
        }
        stockModelVariables.put(stockHoldingModel.stockModel(), mpVariable);
        solver.objective().setCoefficient(mpVariable, 1);
        log.fine(String.format("Added Ticker %s lb: %s",
                                         name, stockHoldingModel.minimumBalance()));
      }
      mpVariables.put(accountModel, stockModelVariables);
    }
    solver.objective().setMaximization();

    // Add constraints that ensure total of stocks in each account is less than
    // or equal to account value
    for (AccountModel account : mpVariables.keySet()) {
      // x1 + x2 + x3 + ... <= ACCOUNT VALUE.
      MPConstraint constraint = solver.makeConstraint(0, account.value());
      log.fine(String.format("Constraint #%s lb: %s, ub: %s", account.name(),
                                       constraint.lb(), constraint.ub()));
      Map<StockModel, MPVariable> variablesByStock = mpVariables.get(account);
      // Add stocks to contraints.
      for (StockModel stock : variablesByStock.keySet()) {
        constraint.setCoefficient(variablesByStock.get(stock), 1);
        otherConstraints.add(constraint);
      }
    }

    // Add constraint that each category is within the wiggle range
    for (CategoryGroupModel categoryGroupModel : stockSolverStorage.getCategoryGroups()) {
      for (CategoryModel category : categoryGroupModel.categories()) {
        double categoryTarget = category.percent() / 100 * totalCash;
        double wiggleRoomCategoryCash = categoryWiggleRoom / 100.0 * categoryTarget;
        MPConstraint constraint = solver.makeConstraint(categoryTarget - wiggleRoomCategoryCash,
                                                        categoryTarget + wiggleRoomCategoryCash);
        log.fine(String.format("Constraint #%s lb: %s, ub: %s", category,
                                         constraint.lb(), constraint.ub()));
        otherConstraints.add(constraint);

        // categoryTarget - wiggleRoomCategoryCash <= stock_1 *
        // percent_in_category + stock_2 + percent_in_category + ... <=
        // categoryTarget + wiggleRoomCategoryGroupCash
        // Find all stocks in the category and add them to the constraint.
        for (AccountModel account : mpVariables.keySet()) {
          Map<StockModel, MPVariable> variablesByStock = mpVariables.get(account);
          for (StockModel stock : variablesByStock.keySet()) {
            if (stock.hasCategoryAllocation(category.name())) {
              // setCoefficient of the percent the stock is in the category
              double percent = stock.percentage(category.name());
              constraint.setCoefficient(variablesByStock.get(stock), percent / 100.0);
            }
          }
        }
      }
    }

    // Add objective that ensure minimum expense ratio
    MPObjective objective = solver.objective();
    for (AccountModel account : mpVariables.keySet()) {
      // Minimize x1 + x2 + x3 + ....
      Map<StockModel, MPVariable> variablesByStock = mpVariables.get(account);
      // Add stocks to obejctive.
      for (StockModel stock : variablesByStock.keySet()) {
        objective.setCoefficient(variablesByStock.get(stock), stock.expenseRatio());
      }
    }
    objective.minimization();

    log.fine("Number of variables = " + solver.numVariables());
    log.fine("Number of constraints = " + solver.numConstraints());

    MPSolver.ResultStatus resultStatus = solver.solve();
    log.fine(resultStatus.toString() + " Solution found");

    // Check that the problem has an optimal solution.
    if (resultStatus != MPSolver.ResultStatus.OPTIMAL) {
      System.err.println("The problem does not have an optimal solution! ");
      return Optional.absent();
    }
    log.fine("Problem solved in " + solver.wallTime() + " milliseconds");
    log.fine("Problem solved in " + solver.iterations() + " iterations");

    // The objective value of the solution.
    log.info("Yearly Fees = " + solver.objective().value()/100);

    // Build account Models for new holdings
    List<AccountModel> newHoldings = new ArrayList<>();
    for (AccountModel account : mpVariables.keySet()) {
      AccountModel.Builder accountModelBuilder = AccountModel.newBuilder();
      double accountActual = 0;
      Map<StockModel, MPVariable> variablesByStock = mpVariables.get(account);
      for (StockModel stockModel : variablesByStock.keySet()) {
        double value = variablesByStock.get(stockModel).solutionValue();
        accountActual += value;
        accountModelBuilder.addStockHoldingModel(
            StockHoldingModel.create(stockModel, 0, false, value));
      }
      newHoldings.add(accountModelBuilder.setName(account.name()).setValue(accountActual).build());
    }

    return Optional.<List<AccountModel>>of(ImmutableList.copyOf(newHoldings));
  }

  private void printDiff(List<AccountModel> currentHoldings, List<AccountModel> newHoldings) {
    Table<String, StockModel, StockHoldingModel> currentHoldingsTable = HashBasedTable.create();
    for (AccountModel account : currentHoldings) {
      for (StockHoldingModel stock : account.stocks()) {
        currentHoldingsTable.put(account.name(), stock.stockModel(), stock);
      }
    }
    // The value of each variable in the solution.
    for (AccountModel account : newHoldings) {
      for (StockHoldingModel newStockHolding: account.stocks()) {
        StockHoldingModel currentHolding =
            currentHoldingsTable.get(account.name(), newStockHolding.stockModel());
        double diff = newStockHolding.currentHolding() - currentHolding.currentHolding();
        log.info(String.format("%s - %s = %s [ Locked? = %s, Min = %s, "
                                         + "old value = %s, diff = %s ]",
                                         account.name(), newStockHolding.stockModel().ticker(),
                                         newStockHolding.currentHolding(),
                                         currentHolding.isLocked(),
                                         currentHolding.minimumBalance(),
                                         currentHolding.currentHolding(),
                                         diff));
      }
    }
  }
}
