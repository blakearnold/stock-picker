package com.theblakearnold.stocksolver;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

import javax.annotation.Nullable;
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

  public void optimizeWiggleRoomAdvanced(final OptimizationProblemType solverType,
      double optimizeTil) {
    // Find overall optimization
    double overallWiggle = findOverallWiggle(solverType, optimizeTil);

    // Find each category optimization.
    Map<String, Double> baseCategoryWiggles = buildCategoryMap(overallWiggle);
    List<String> categories = ImmutableList.copyOf(baseCategoryWiggles.keySet());
    Set<List<String>> tried = new HashSet<>();
    List<Map<String, Double>> solutions = new ArrayList<>();
    Map<String, Double> lowestSolution = null;
    double lowestAverage = 100;
    // Look through 1000 random combos.

    // TODO(blake): We should probably find dependent categories and mess with those
    // instead of just finding random orders.
    for (int i = 0; i < 100; i++) {
      log.info("Trying " + i);
      List<String> categoriesPerm = buildRandomOrder(categories);
      if (!tried.add(categoriesPerm)) {
        log.info("Skipping");
        continue;
      }
      Map<String, Double> categoryWiggles = new HashMap<>(baseCategoryWiggles);
      log.info("Optimizing with order: " + categoriesPerm);
      for (String category : categoriesPerm) {
        double categoryWiggle = findCategoryWiggle(solverType, optimizeTil, categoryWiggles,
            category);
        categoryWiggles.put(category, categoryWiggle);
      }
      double average = calculateAverage(categoryWiggles.values());
      if (average < lowestAverage) {
        lowestAverage = average;
        lowestSolution = categoryWiggles;
        log.info(String.format("found next smallest %s", average));
      }
      solutions.add(categoryWiggles);
    }

    log.info(String.format("Smallest wiggle found %s", lowestSolution));
    Optional<List<AccountModel>> solution = runSolver(solverType, lowestSolution, true);
    printDiff(stockSolverStorage.getAccounts(), solution.get());
    printPercentage(solution.get());
  }

  private double calculateAverage(Collection<Double> values) {
    double total = 0;
    for (Double value : values) {
      total += value;
    }
    return total/values.size();
  }

  private double findOverallWiggle(final OptimizationProblemType solverType,
      double optimizeTil) {
    Optional<Double> overallOptimization =
        binarySearch(optimizeTil, 100, new Function<Double, Boolean>() {
          @Override
          public Boolean apply(Double wigglePercent) {
            Optional<List<AccountModel>> solution = runSolver(
                solverType, buildCategoryMap(wigglePercent), false);
            return solution.isPresent();
          }
        });
    if (!overallOptimization.isPresent()) {
      log.warning("Failed to optimize wiggle percent.");
      throw new RuntimeException("Failed to optimize");
    }
    log.info(String.format("Overall Optimized with percent %s.", overallOptimization.get()));
    return overallOptimization.get();
  }

  private double findCategoryWiggle(final OptimizationProblemType solverType,
      double optimizeTil, final Map<String, Double> categoryWiggle, final String category) {
    Optional<Double> overallOptimization =
        binarySearch(optimizeTil, categoryWiggle.get(category), new Function<Double, Boolean>() {
          @Override
          public Boolean apply(Double wigglePercent) {
            Map<String, Double> modifiedCategoryWiggle = new HashMap<>(categoryWiggle);
            modifiedCategoryWiggle.put(category, wigglePercent);
            Optional<List<AccountModel>> solution = runSolver(solverType, modifiedCategoryWiggle,
                false);
            return solution.isPresent();
          }
        });
    if (!overallOptimization.isPresent()) {
      log.warning("Failed to optimize wiggle percent.");
      throw new RuntimeException("Failed to optimize");
    }
    return overallOptimization.get();
  }

  private <T> List<T> buildRandomOrder(List<T> items) {
    List<T> copyOfList = new ArrayList<>(items);
    ImmutableList.Builder<T> result = ImmutableList.builder();
    Random random = new Random();
    while (copyOfList.size() > 1) {
      result.add(copyOfList.remove(random.nextInt(copyOfList.size())));
    }
    result.addAll(copyOfList);
    return result.build();
  }

  /**
   * Searches for a successful run, minimizing the input.
   * Returns the minimum input found that runs successfully.
   */
  private Optional<Double> binarySearch(double optimizeTil, double maxBound,
      Function<Double, Boolean> function) {
    double lowerBound = 0;
    double upperBound = 100;
    Optional<Double> lastGoodPercent= Optional.absent();
    // Run until we hit the optimize percent, or the last run was not successful.
    while (Math.abs(lowerBound - upperBound) > optimizeTil) {
      double wigglePercent = (lowerBound + upperBound) / 2;
      boolean result = function.apply(wigglePercent);
      if (result) {
        upperBound = wigglePercent;
        lastGoodPercent = Optional.of(wigglePercent);
      } else {
        lowerBound = wigglePercent;
      }
    }
    return lastGoodPercent;

  }

  private Map<String, Double> buildCategoryMap(double wigglePercent) {
    ImmutableMap.Builder wigglePercentsBuilder = ImmutableMap.builder();
    for (CategoryGroupModel categoryGroupModel : stockSolverStorage.getCategoryGroups()) {
      for (CategoryModel categoryModel : categoryGroupModel.categories()) {
        wigglePercentsBuilder.put(categoryModel.name(), wigglePercent);
      }
    }
    return wigglePercentsBuilder.build();
  }

  public Optional<List<AccountModel>> runSolver(
      MPSolver.OptimizationProblemType solverType, Map<String, Double> categoryWiggleRoom,
      boolean debugOn) {
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
        log.fine(String.format("Added Ticker %s lb: %s",
                                         name, stockHoldingModel.minimumBalance()));
      }
      mpVariables.put(accountModel, stockModelVariables);
    }

    // Add constraints that ensure total of stocks in each account is less than
    // or equal to account value
    for (AccountModel account : mpVariables.keySet()) {
      // x1 + x2 + x3 + ... <= ACCOUNT VALUE.
      MPConstraint constraint = solver.makeConstraint(account.value(), account.value());
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
        double wiggleRoomCategoryCash =
            categoryWiggleRoom.get(category.name()) / 100.0 * categoryTarget;
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
      return Optional.absent();
    }
    log.fine("Problem solved in " + solver.wallTime() + " milliseconds");
    log.fine("Problem solved in " + solver.iterations() + " iterations");

    if (debugOn) {
      // The objective value of the solution.
      log.info("Yearly Fees = " + solver.objective().value()/100);
    }

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
    Map<String, Double> currentAccountValues = new HashMap<>();
    for (AccountModel account : currentHoldings) {
      currentAccountValues.put(account.name(), account.value());
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
                + "old value = %s, diff = %s, percent = %s ]",
            account.name(), newStockHolding.stockModel().ticker(),
            newStockHolding.currentHolding(),
            currentHolding.isLocked(),
            currentHolding.minimumBalance(),
            currentHolding.currentHolding(),
            diff,
            newStockHolding.currentHolding() / account.value() * 100));
      }
      log.info(String.format("%s: Cash Invested: %s of %s", account.name(), account.value(),
          currentAccountValues.get(account.name())));
    }
  }
}
