package com.theblakearnold.stocksolver;

import com.google.common.base.Objects;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;

import com.theblakearnold.stocksolver.model.AccountModel;
import com.theblakearnold.stocksolver.model.CategoryGroupModel;
import com.theblakearnold.stocksolver.model.CategoryModel;
import com.theblakearnold.stocksolver.model.StockHoldingModel;
import com.theblakearnold.stocksolver.storage.StockSolverStorage;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Logger;

import javax.inject.Inject;

/**
 * Runs advanced validation on input.
 *
 * <p> This will do things like, make sure you have enough stocks for each category.
 */
public class InputValidator {
  private final static Logger log = Logger.getLogger(InputValidator.class.getName());

  private final static NumberFormat formatter = new DecimalFormat("#0.00");;

  private final StockSolverStorage stockSolverStorage;
  private double totalValue;

  @Inject
  public InputValidator(StockSolverStorage stockSolverStorage) {
    this.stockSolverStorage = stockSolverStorage;
  }

  public void validate() {
    precompute();
    accountsHaveCategories();
  }

  /**
   * Sets local variables.
   */
  private void precompute() {
    for (AccountModel account : stockSolverStorage.getAccounts()) {
      totalValue += account.value();
    }
  }

  /**
   * Ensures accounts have
   * 1. enough money in each category to hit the target
   * 2. for categories that only target one account, the total dependent amount is less than the
   * accounts value
   */
  private void accountsHaveCategories() {
    // Map of account names to the categories fully reliant amount to hit the target.
    Multimap<String, DependentAccountValue> reliantCategoriesByAccountName =
        LinkedListMultimap.create();
    Map<String, Double> targetValueByCategory = new HashMap<>();
    for (CategoryGroupModel categoryGroupModel : stockSolverStorage.getCategoryGroups()) {
      for (CategoryModel category : categoryGroupModel.categories()) {
        double targetValue = category.percent() * totalValue / 100;
        targetValueByCategory.put(category.name(), targetValue);
        double maxTotalValue = 0;
        Map<String, Double> maxAccountCategoryValueByAccount = new HashMap<>();
        for (AccountModel account : stockSolverStorage.getAccounts()) {
          double maxAccountValueOfCategory = 0;
          for (StockHoldingModel stockHoldingModel : account.stocks()) {
            if (stockHoldingModel.stockModel().hasCategoryAllocation(category.name())) {
              double percent = stockHoldingModel.stockModel().percentage(category.name()) / 100;
              if (stockHoldingModel.isLocked()) {
                // If locked, only add current value percent.
                maxAccountValueOfCategory += stockHoldingModel.currentHolding() * percent;
              } else {
                maxAccountValueOfCategory += account.value() * percent;
              }
            }
          }
          maxAccountCategoryValueByAccount.put(account.name(), maxAccountValueOfCategory);
          maxTotalValue += maxAccountValueOfCategory;
          log.info(String.format("Category %s - Account %s - Target: $%s, Max avail: $%s",
              category.name(), account.name(), formatter.format(targetValue),
              formatter.format(maxAccountValueOfCategory)));
        }
        DependentAccountValue relientAccountValue = calculateReliantAccount(category.name(),
            targetValue, maxAccountCategoryValueByAccount);
        if (relientAccountValue.dependentValue > 0) {
          reliantCategoriesByAccountName.put(relientAccountValue.accountName, relientAccountValue);
        }
        log.info(String.format("Category %s - Target: $%s, Max avail: $%s",
            category.name(), formatter.format(targetValue), formatter.format(maxTotalValue)));
        if (maxTotalValue < targetValue) {
          throw new IllegalArgumentException("Accounts cant buy enough for category "
              + category.name());
        }
      }
    }
    log.info(reliantCategoriesByAccountName.toString());
    verifyDependentAmountWorks(reliantCategoriesByAccountName);
    verifyDependentAmountWorksDeeper(reliantCategoriesByAccountName, targetValueByCategory);
  }

  private void verifyDependentAmountWorks(
      Multimap<String, DependentAccountValue> reliantCategoriesByAccountName) {
    for (AccountModel account : stockSolverStorage.getAccounts()) {
      if (reliantCategoriesByAccountName.containsKey(account.name())) {
        double sumForAccount = 0;
        for (DependentAccountValue dependentAccountValue :
            reliantCategoriesByAccountName.get(account.name())) {
          sumForAccount += dependentAccountValue.dependentValue;
        }
        if (sumForAccount > account.value()) {
          throw new IllegalArgumentException(String.format(
              "Too many categories are dependent on account: %s, Categories: %s",
              account.name(), reliantCategoriesByAccountName.get(account.name())));
        } else {
          log.info(String.format(
              "Categories that are dependent on an account: %s, Categories: %s",
              account.name(), reliantCategoriesByAccountName.get(account.name())));
        }
      }
    }
  }

  private void verifyDependentAmountWorksDeeper(
      Multimap<String, DependentAccountValue> reliantCategoriesByAccountName,
      Map<String, Double> targetValueByCategory) {
    boolean failures = false;
    for (AccountModel account : stockSolverStorage.getAccounts()) {
      if (reliantCategoriesByAccountName.containsKey(account.name())) {
        double sumForAccount = 0;
        for (DependentAccountValue dependentAccountValue :
            reliantCategoriesByAccountName.get(account.name())) {
          boolean successful = false;
          for (StockHoldingModel stockHoldingModel : account.stocks()) {
            if (stockHoldingModel.stockModel().hasCategoryAllocation(
                dependentAccountValue.category)) {
              double percent = stockHoldingModel.stockModel().percentage(
                  dependentAccountValue.category) / 100;
              double totalStockPrice = dependentAccountValue.dependentValue / percent;
              boolean succeededForThisStock = true;
              for (String category : targetValueByCategory.keySet()) {
                if (stockHoldingModel.stockModel().hasCategoryAllocation(category)) {
                  double categoryValueIfPurchased =
                      stockHoldingModel.stockModel().percentage(category) / 100 * totalStockPrice;
                  if (categoryValueIfPurchased > targetValueByCategory.get(category)) {
                    double offBy = categoryValueIfPurchased - targetValueByCategory.get(category);
                    log.warning(String.format(
                        "Stock %s in account %s must be purchased for category %s, but doing so "
                            + "puts category %s over its target value by %s - %s%%",
                        stockHoldingModel.stockModel().ticker(),
                        account.name(),
                        dependentAccountValue.category,
                        category,
                        offBy,
                        offBy / totalValue * 100));
                    succeededForThisStock = false;
                  }
                }
              }
              successful |= succeededForThisStock;
            }
          }
          failures |= !successful;

        }
      }
    }
    if (failures) {
      throw new IllegalArgumentException(String.format(
          "Unable to find stock for some categories"));
    }
  }

  private DependentAccountValue calculateReliantAccount(String category, double categoryTarget,
      Map<String, Double> maxAccountCategoryValueByAccount) {
    SortedSet<Map.Entry<String, Double>> sortedEntries =
        entriesSortedByValues(maxAccountCategoryValueByAccount);
    Map.Entry<String, Double> largest = sortedEntries.last();
    double valueLeft = categoryTarget;
    for (Map.Entry<String, Double> smallerEntry : sortedEntries.headSet(largest)) {
      valueLeft -= smallerEntry.getValue();
    }
    return new DependentAccountValue(largest.getKey(), valueLeft, category, categoryTarget);
  }

  static <K,V extends Comparable<? super V>> SortedSet<Map.Entry<K,V>> entriesSortedByValues(
      Map<K,V> map) {
    SortedSet<Map.Entry<K,V>> sortedEntries = new TreeSet<Map.Entry<K,V>>(
        new Comparator<Map.Entry<K,V>>() {
          @Override public int compare(Map.Entry<K,V> e1, Map.Entry<K,V> e2) {
            return e1.getValue().compareTo(e2.getValue());
          }
        }
    );
    sortedEntries.addAll(map.entrySet());
    return sortedEntries;
  }

  class DependentAccountValue {
    private final String accountName;
    private final double dependentValue;
    private final String category;
    private final double categoryTarget;

    public DependentAccountValue(String accountName, double dependentValue, String category,
        double categoryTarget) {
      this.accountName = accountName;
      this.dependentValue = dependentValue;
      this.category = category;
      this.categoryTarget = categoryTarget;
    }

    @Override
    public String toString() {
      return String.format("Category: %s - Dependent Value: %s", category, dependentValue);
//      return Objects.toStringHelper(this)
//          .add("category", category)
//          .add("categoryTarget", categoryTarget)
//          .add("accountName", accountName)
//          .add("dependentValue", dependentValue)
//          .toString();
    }
  }

}
