package com.theblakearnold.stocksolver.model;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class StockHoldingModel {

  StockHoldingModel() {
  }

  public static StockHoldingModel create(StockModel stockModel, double minimumBalance) {
    return new AutoValue_StockHoldingModel(stockModel, minimumBalance, false, 0);
  }

  public static StockHoldingModel create(StockModel stockModel, double minimumBalance,
                                         boolean locked, double currentHolding) {
    return new AutoValue_StockHoldingModel(stockModel, minimumBalance, locked, currentHolding);
  }

  public abstract StockModel stockModel();

  public abstract double minimumBalance();

  public abstract boolean isLocked();

  public abstract double currentHolding();

}
