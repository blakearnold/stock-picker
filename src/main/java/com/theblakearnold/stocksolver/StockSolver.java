package com.theblakearnold.stocksolver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;
import com.theblakearnold.stocksolver.model.AccountModel;
import com.theblakearnold.stocksolver.model.CategoryGroupModel;
import com.theblakearnold.stocksolver.model.CategoryModel;
import com.theblakearnold.stocksolver.model.StockHoldingModel;
import com.theblakearnold.stocksolver.model.StockModel;
import com.theblakearnold.stocksolver.storage.StockSolverStorage;

/**
 * Linear programming example that shows how to use the API.
 *
 */

public class StockSolver {

	private final StockSolverStorage stockSolverStorage;

  @Inject
  public StockSolver(StockSolverStorage stockSolverStorage) {
    this.stockSolverStorage = stockSolverStorage;
  }

  static {
    System.loadLibrary("jniortools");
  }

  private static MPSolver createSolver(String solverType) {
    try {
      return new MPSolver("IntegerProgrammingExample", MPSolver.getSolverEnum(solverType));
    } catch (java.lang.ClassNotFoundException e) {
      throw new Error(e);
    } catch (java.lang.NoSuchFieldException e) {
      return null;
    } catch (java.lang.IllegalAccessException e) {
      throw new Error(e);
    }
  }

  public void optimizeWiggleRoom(String solverType, double optimizeTil) {
    double lowerBound = 0;
    double upperBound = 100;
    Double lastGoodPercent = null;
    int count = 0;
    // Run until we hit the optimize percent, or the last run was not successful.
    while (Math.abs(lowerBound - upperBound) > optimizeTil) {
      count++;
      double wigglePercent = (lowerBound + upperBound) / 2;
      System.out.println("Trying wiggle room at " + wigglePercent + "%");
      if (runLinearProgrammingExample(solverType, wigglePercent)) {
        upperBound = wigglePercent;
        lastGoodPercent = wigglePercent;
      } else {
        lowerBound = wigglePercent;
      }
    }
    if (lastGoodPercent == null) {
      System.out.println("Failed to optimize wiggle percent.");
    } else {
      runLinearProgrammingExample(solverType, lastGoodPercent);
      System.out.println(String.format("Optimized in %s tries, with percent %s.",
          count, lastGoodPercent));
    }
  }

  public boolean runLinearProgrammingExample(String solverType, double categoryWiggleRoom) {
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
        System.out.println(String.format("Added Ticker %s lb: %s",
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
      System.out.println(String.format("Constraint #%s lb: %s, ub: %s", account.name(),
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
        System.out.println(String.format("Constraint #%s lb: %s, ub: %s", category,
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

    System.out.println("Number of variables = " + solver.numVariables());
    System.out.println("Number of constraints = " + solver.numConstraints());

    int resultStatus = solver.solve();

    if (resultStatus == MPSolver.OPTIMAL) {
      System.out.println("Optimal Solution found");
    } else if (resultStatus == MPSolver.FEASIBLE) {
      System.out.println("FEASIBLE Solution found");
    } else if (resultStatus == MPSolver.INFEASIBLE) {
      System.out.println("INFEASIBLE Solution found");
    } else if (resultStatus == MPSolver.UNBOUNDED) {
      System.out.println("UNBOUNDED Solution found");
    } else if (resultStatus == MPSolver.ABNORMAL) {
      System.out.println("ABNORMAL Solution found");
    } else if (resultStatus == MPSolver.NOT_SOLVED) {
      System.out.println("NOT_SOLVED Solution found");
    }

    // Check that the problem has an optimal solution.
    if (resultStatus != MPSolver.OPTIMAL) {
      System.err.println("The problem does not have an optimal solution! ");
      return false;
    }

    System.out.println("Problem solved in " + solver.wallTime() + " milliseconds");

    // The objective value of the solution.
    System.out.println("Optimal objective value = " + solver.objective().value());

    // The value of each variable in the solution.
    for (AccountModel account : mpVariables.keySet()) {
      Map<StockModel, MPVariable> variablesByStock = mpVariables.get(account);
      Map<String, StockHoldingModel> stockHoldingByTicker = new HashMap<>();
      for (StockHoldingModel stockHolding : account.stocks()) {
        stockHoldingByTicker.put(stockHolding.stockModel().ticker(), stockHolding);
      }
      for (StockModel stock : variablesByStock.keySet()) {
        double diff = variablesByStock.get(stock).solutionValue()
            - stockHoldingByTicker.get(stock.ticker()).minimumBalance();
        System.out.println(String.format("%s - %s = %s [ minimum value = %s - diff: %s ]",
            account.name(), stock.ticker(), variablesByStock.get(stock).solutionValue(),
            stockHoldingByTicker.get(stock.ticker()).minimumBalance(), diff));
      }
    }

    System.out.println("Advanced usage:");
    System.out.println("Problem solved in " + solver.iterations() + " iterations");

    for (CategoryGroupModel categoryGroupModel : stockSolverStorage.getCategoryGroups()) {
      double categoryGroupTargetPercentage = 0;
      double categoryGroupTarget = 0;
      double categoryGroupActual = 0;
      System.out.println("---Category Group---" + categoryGroupModel.name());
      for (CategoryModel category : categoryGroupModel.categories()) {
        double categoryTarget = category.percent() / 100.0 * totalCash;
        categoryGroupTarget += categoryTarget;
        categoryGroupTargetPercentage += category.percent();
        double actual = 0;

        for (AccountModel account : mpVariables.keySet()) {
          Map<StockModel, MPVariable> variablesByStock = mpVariables.get(account);
          for (StockModel stock : variablesByStock.keySet()) {
            if (stock.hasCategoryAllocation(category.name())) {
              // setCoefficient of the percent the stock is in the category
              double percent = stock.percentage(category.name());
              actual += variablesByStock.get(stock).solutionValue() * (percent / 100.0);
            }
          }
        }
        categoryGroupActual += actual;
        System.out.println(String.format("%s: Target %s, %s, Actual %s, %s", category,
            categoryTarget, category.percent(), actual, actual / totalCash * 100));
      }
      System.out.println(String.format("--- GROUP Totals: Target %s, %s, Actual %s, %s",
          categoryGroupTarget, categoryGroupTargetPercentage, categoryGroupActual,
          categoryGroupActual / totalCash * 100));
    }
    double actualTotalCash = 0;
    for (AccountModel account : mpVariables.keySet()) {
      double accountActual = 0;
      Map<StockModel, MPVariable> variablesByStock = mpVariables.get(account);
      for (MPVariable mpVariable : variablesByStock.values()) {
        accountActual += mpVariable.solutionValue();
      }
      System.out.println(String.format("%s: Cash Invested: Target %s. Actual %s",
          account.name(), account.value(), accountActual));
      actualTotalCash += accountActual;
    }
    System.out.println(String.format("Total Cash Invested: Target %s. Actual %s", totalCash,
        actualTotalCash));
    return true;
  }
}
