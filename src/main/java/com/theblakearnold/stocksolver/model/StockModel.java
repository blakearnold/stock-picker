package com.theblakearnold.stocksolver.model;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;

import java.util.HashMap;
import java.util.Map;

@AutoValue
public abstract class StockModel {

  StockModel() {
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public abstract String ticker();

  abstract Map<String, Double> percentages();

  public double percentage(String categoryName) {
    return percentages().get(categoryName);
  }

  public boolean hasCategoryAllocation(String categoryName) {
    return percentages().containsKey(categoryName);
  }

  public static class Builder {

    private final Map<String, Double> percentages = new HashMap<>();
    private String ticker;

    private Builder() {
    }

    public Builder setAllocation(CategoryModel createCategoryModel) {
      percentages.put(createCategoryModel.name(), createCategoryModel.percent());
      return this;
    }

    public Builder setTicker(String ticker) {
      this.ticker = ticker;
      return this;
    }

    public StockModel build() {
      if (ticker == null || ticker.isEmpty()) {
        throw new IllegalStateException("ticker is empty");
      }
      double percentageTotal = 0;
      for (Double percentage : percentages.values()) {
        percentageTotal += percentage;
      }
      if (percentageTotal != 100) {
        throw new IllegalStateException(
            ticker + " percentage total must be 100, was: " + percentageTotal);
      }
      return new AutoValue_StockModel(ticker, ImmutableMap.copyOf(percentages));
    }
  }
}
