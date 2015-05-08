package com.theblakearnold.stocksolver;

import com.theblakearnold.stocksolver.model.AccountModel;
import com.theblakearnold.stocksolver.model.CategoryGroupModel;
import com.theblakearnold.stocksolver.model.CategoryModel;
import com.theblakearnold.stocksolver.model.StockHoldingModel;
import com.theblakearnold.stocksolver.storage.StockSolverStorage;

import java.text.DecimalFormat;
import java.text.NumberFormat;
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

  private void accountsHaveCategories() {
    for (CategoryGroupModel categoryGroupModel : stockSolverStorage.getCategoryGroups()) {
      for (CategoryModel category : categoryGroupModel.categories()) {
        double targetValue = category.percent() * totalValue / 100;
        double maxTotalValue = 0;
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
          maxTotalValue += maxAccountValueOfCategory;
          log.info(String.format("Category %s - Account %s - Target: $%s, Max avail: $%s",
              category.name(), account.name(), formatter.format(targetValue),
              formatter.format(maxAccountValueOfCategory)));
        }
        log.info(String.format("Category %s - Target: $%s, Max avail: $%s",
            category.name(), formatter.format(targetValue), formatter.format(maxTotalValue)));
        if (maxTotalValue < targetValue) {
          throw new IllegalArgumentException("Accounts cant buy enough for category "
              + category.name());
        }
      }
    }

  }

}
