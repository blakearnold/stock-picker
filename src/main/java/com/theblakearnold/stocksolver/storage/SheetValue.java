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

  /**
   * Returns the double value.
   *
   * @throws IllegalArgumentException thrown if the value is not a double
   */
  public double getDouble() {
    if (!Type.DOUBLE.equals(type())) {
      throw new IllegalArgumentException("Not a double");
    }
    return doubleValue();
  }

  // Tries to get the double, either by parsing the string value
  // or returning the double directly.
  public Double getDoubleWithParsing() {
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
