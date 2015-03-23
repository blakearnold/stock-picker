package com.theblakearnold.stocksolver.model;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class CategoryModel {

  CategoryModel() {
  }

  public abstract String name();

  public abstract double percent();

  public static CategoryModel create(String name, double targetPercent) {
    return new AutoValue_CategoryModel(name, targetPercent);
  }
}
