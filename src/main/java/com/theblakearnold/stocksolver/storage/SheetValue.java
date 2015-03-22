package com.theblakearnold.stocksolver.storage;

import com.google.auto.value.AutoValue;

import javax.annotation.Nullable;

@AutoValue
public abstract class SheetValue {

  public static enum Type {
    STRING,
    DOUBLE;
  }

  @Nullable
  public abstract Type type();

  @Nullable
  abstract String stringValue();

  abstract double doubleValue();

  SheetValue() {
  }

  public static SheetValue createSheetValue(double doubleValue) {
    return new AutoValue_SheetValue(Type.DOUBLE, null, doubleValue);
  }

  public static SheetValue createSheetValue(String stringValue) {
    return new AutoValue_SheetValue(Type.STRING, stringValue, 0);
  }

  public String getString() {
    if (!Type.STRING.equals(type())) {
      throw new IllegalArgumentException("Not a string, its a " + type());
    }
    return stringValue();
  }

  public double getDouble() {
    if (!Type.DOUBLE.equals(type())) {
      throw new IllegalArgumentException("Not a double");
    }
    return doubleValue();
  }

  public Double getDoubleWithParsing() {
    if (Type.DOUBLE.equals(type())) {
      return doubleValue();
    }
    if (Type.STRING.equals(type())) {

    }
    switch (type()) {
      case DOUBLE:
        return doubleValue();
      case STRING:
        try {
          return Double.parseDouble(stringValue());
        } catch (NumberFormatException e) {
          // Fall through below.
        }
      default:
        System.out.println("Not a double-like value: " + this);
        return null;
    }
  }
}
