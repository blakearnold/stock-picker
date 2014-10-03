package com.theblakearnold.stocksolver.model;

import java.util.ArrayList;
import java.util.List;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

@AutoValue
public abstract class AccountModel {

	AccountModel() {}

	public static Builder newBuilder() {
		return new Builder();
	}

	public abstract String name();
	public abstract double value();
	public abstract List<StockHoldingModel> stocks();


	public static class Builder {
		private final List<StockHoldingModel> stocks = new ArrayList<>();
		private double value;
		private String name;

		private Builder() {}

		public Builder addStockHoldingModel(StockHoldingModel model) {
			Preconditions.checkNotNull(model);
			stocks.add(model);
			return this;
		}

		public Builder setValue(double value) {
			this.value = value;
			return this;
		}

		public Builder setName(String name) {
			this.name = name;
			return this;
		}

		public AccountModel build() {
			if (name.isEmpty()) {
				throw new IllegalStateException("name not set");
			}
      if (stocks.isEmpty()) {
        throw new IllegalStateException("no stocks added to " + name);
      }
			return new AutoValue_AccountModel(name, value, ImmutableList.copyOf(stocks));
		}
	}
}
