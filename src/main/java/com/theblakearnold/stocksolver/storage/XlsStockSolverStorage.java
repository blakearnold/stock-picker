package com.theblakearnold.stocksolver.storage;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Table;
import com.google.common.io.Closeables;

import com.theblakearnold.stocksolver.model.AccountModel;
import com.theblakearnold.stocksolver.model.CategoryGroupModel;
import com.theblakearnold.stocksolver.model.CategoryGroupModel.Builder;
import com.theblakearnold.stocksolver.model.CategoryModel;
import com.theblakearnold.stocksolver.model.StockHoldingModel;
import com.theblakearnold.stocksolver.model.StockModel;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

public class XlsStockSolverStorage implements StockSolverStorage {

  private static final String STOCKS_SHEET_NAME = "Stocks";
  private static final String ALLOCATIONS_SHEET_NAME = "Allocations";
  private static final String HOLDINGS_SHEET_NAME = "Holdings";

  private static final String PERCENT_COLUMN_NAME = "Percent";
  private static final String ACCOUNT_COLUMN_NAME = "Account";
  private static final String CURRENT_VALUE_COLUMN_NAME = "Current Value";
  private static final String MIN_VALUE_COLUMN_NAME = "Min Value";
  private static final String LOCKED_COLUMN_NAME = "Locked until";
  private static final String TICKER_COLUMN_NAME = "Ticker";
  private static final String CATEGORY_COLUMN_NAME = "Category";
  private static final String GROUP_COLUMN_NAME = "Group";

  private final String filename;
  private final Map<String, StockModel> stockModelByTicker;
  private final List<AccountModel> accountModel;
  private final List<CategoryGroupModel> categoryGroups;

  public XlsStockSolverStorage(String filename) {
    this.filename = filename;

    // TODO: make these immutable.
    this.stockModelByTicker = new HashMap<>();
    this.accountModel = new ArrayList<>();
    this.categoryGroups = new ArrayList<>();
  }

  public void load() throws IOException, InvalidFormatException {
    OPCPackage pkg = OPCPackage.open(filename);
    try {
      XSSFWorkbook wb = new XSSFWorkbook(pkg);

      {
        Sheet sheet = wb.getSheet(STOCKS_SHEET_NAME);
        if (sheet == null) {
          throw new IllegalArgumentException("Input excel file is missing sheet: "
                                             + STOCKS_SHEET_NAME);
        }
        Table<Map<String, SheetValue>, String, SheetValue> stockTable =
            parseSheet(sheet, TICKER_COLUMN_NAME);
        parseStockTable(stockTable);
      }

      {
        Sheet allocationsSheet = wb.getSheet(ALLOCATIONS_SHEET_NAME);
        if (allocationsSheet == null) {
          throw new IllegalArgumentException("Input excel file is missing sheet: "
                                             + ALLOCATIONS_SHEET_NAME);
        }
        Table<Map<String, SheetValue>, String, SheetValue> allocationsTable =
            parseSheet(allocationsSheet, CATEGORY_COLUMN_NAME);
        parseAllocationsTable(allocationsTable);
      }

      {
        Sheet holdingsSheet = wb.getSheet(HOLDINGS_SHEET_NAME);
        if (holdingsSheet == null) {
          throw new IllegalArgumentException("Input excel file is missing sheet: "
                                             + HOLDINGS_SHEET_NAME);
        }
        Table<Map<String, SheetValue>, String, SheetValue> holdingsTable =
            parseSheet(holdingsSheet, TICKER_COLUMN_NAME, ACCOUNT_COLUMN_NAME);
        parseHoldingsTable(holdingsTable);
      }
    } finally {
      try {
        Closeables.close(pkg, true);
      } catch (Throwable t) {
        System.out.println("Mehh... ");
        t.printStackTrace();
      }
    }
  }

  private void parseHoldingsTable(
      Table<Map<String, SheetValue>, String, SheetValue> holdingsTable) {
    Map<String, AccountModel.Builder> accountModelBuilderByAccountName = new HashMap<>();
    Map<String, Double> valueByAccountName = new HashMap<>();
    for (Map<String, SheetValue> keyMap : holdingsTable.rowKeySet()) {
      SheetValue tickerSheetValue = keyMap.get(TICKER_COLUMN_NAME);
      String ticker = tickerSheetValue.getString();
      SheetValue accountSheetValue = keyMap.get(ACCOUNT_COLUMN_NAME);
      String accountName = accountSheetValue.getString();

      SheetValue holdingsSheetValue = holdingsTable.get(keyMap, CURRENT_VALUE_COLUMN_NAME);
      if (holdingsSheetValue == null) {
        System.out.println("skipping adding value: " + holdingsSheetValue + " : " + ticker);
      } else {
        Double tickerValue = holdingsSheetValue.getDoubleWithParsing();
        if (tickerValue == null || tickerValue == 0) {
          System.out.println("skipping adding value: " + holdingsSheetValue + " : " + ticker);
        } else {
          Double value = valueByAccountName.get(accountName);
          double newValue = value == null ? tickerValue : tickerValue + value;
          valueByAccountName.put(accountName, newValue);
        }
      }

      if (!stockModelByTicker.containsKey(ticker)) {
        System.out.println("skipping adding ticker to account because not defined in stocks sheet: "
                           + ticker);
        continue;
      }

      double minValue = 0;
      SheetValue minValueSheetValue = holdingsTable.get(keyMap, MIN_VALUE_COLUMN_NAME);
      if (minValueSheetValue == null) {
        System.out.println(String.format("skipping adding min value for ticker %s,"
                                         + " missing column: %s", ticker, MIN_VALUE_COLUMN_NAME));
      } else {

        Double minValueParsed = minValueSheetValue.getDoubleWithParsing();
        if (minValueParsed == null) {
          System.out.println("skipping adding value: " + minValueParsed + " : " + ticker);
        } else {
          minValue = minValueParsed;
        }
      }
      boolean locked = false;
      SheetValue lockedSheetValue = holdingsTable.get(keyMap, LOCKED_COLUMN_NAME);
      if (lockedSheetValue == null) {
        System.out.println("setting locked value to false: " + ticker);
        locked = false;
      } else {
        Double lockedValueParsed = lockedSheetValue.getDoubleWithParsing();
        // For now, have anything non zero in the locked column signify locked.
        if (lockedValueParsed == null || lockedValueParsed.doubleValue() == 0) {
          System.out.println("setting locked value to false: " + ticker);
          locked = false;
        } else {
          System.out.println("setting locked value to true: " + ticker);
          locked = true;
        }
      }
      AccountModel.Builder accountModelBuilder = accountModelBuilderByAccountName.get(accountName);
      if (accountModelBuilder == null) {
        accountModelBuilder = AccountModel.newBuilder().setName(accountName);
        accountModelBuilderByAccountName.put(accountName, accountModelBuilder);
      }
      accountModelBuilder.addStockHoldingModel(
          StockHoldingModel.create(stockModelByTicker.get(ticker), minValue, locked));

    }
    for (String accountName : accountModelBuilderByAccountName.keySet()) {
      Double accountValue = valueByAccountName.get(accountName);
      if (accountValue == null) {
        System.out.println("skipping account with zero value: " + accountName);
        continue;
      }
      accountModelBuilderByAccountName.get(accountName).setValue(accountValue).build();
      accountModel.add(accountModelBuilderByAccountName.get(accountName)
                           .setValue(valueByAccountName.get(accountName)).build());
    }
    System.out.println(accountModel);
  }

  private void parseAllocationsTable(
      Table<Map<String, SheetValue>, String, SheetValue> allocationsTable) {
    Map<String, CategoryGroupModel.Builder> cateogryGroupByGroupName = new HashMap<>();
    String defaultCategoryName = "Default";
    for (Map<String, SheetValue> keySet : allocationsTable.rowKeySet()) {
      SheetValue categorySheetValue = keySet.get(CATEGORY_COLUMN_NAME);
      String category = categorySheetValue.getString();
      if ("Validation".equals(category)) {
        continue;
      }
      SheetValue sheetValue = allocationsTable.get(keySet, PERCENT_COLUMN_NAME);
      if (sheetValue == null || !SheetValue.Type.DOUBLE.equals(sheetValue.type())
          || sheetValue.doubleValue() == 0) {
        System.out.println("skipping value: " + sheetValue);
        continue;
      }
      SheetValue groupSheetValue = allocationsTable.get(keySet, GROUP_COLUMN_NAME);
      final String groupName;
      if (groupSheetValue == null) {
        System.out.println("no value for group, adding to default: " + defaultCategoryName);
        groupName = defaultCategoryName;
      } else {
        String extractedString = groupSheetValue.getString();
        if (extractedString == null) {
          groupName = defaultCategoryName;
        } else {
          groupName = extractedString;
        }
      }
      if (!cateogryGroupByGroupName.containsKey(groupName)) {
        cateogryGroupByGroupName.put(
            groupName, CategoryGroupModel.newBuilder().setName(groupName));
      }
      Builder categoryGroupModelBuilder = cateogryGroupByGroupName.get(groupName);
      categoryGroupModelBuilder.addCategory(
          CategoryModel.create(category, 100 * sheetValue.doubleValue()));
    }
    for (CategoryGroupModel.Builder categoryGroupModelBuilder : cateogryGroupByGroupName.values()) {
      categoryGroups.add(categoryGroupModelBuilder.build());
    }
    System.out.println(categoryGroups);

  }

  private void parseStockTable(Table<Map<String, SheetValue>, String, SheetValue> stockTable) {
    for (Map<String, SheetValue> keySet : stockTable.rowKeySet()) {
      SheetValue tickerSheetValue = keySet.get(TICKER_COLUMN_NAME);
      String ticker = tickerSheetValue.getString();
      StockModel.Builder stockModelBuilder = StockModel.newBuilder();
      stockModelBuilder.setTicker(ticker);
      Map<String, SheetValue> valuesByCategory = stockTable.row(keySet);
      for (String category : valuesByCategory.keySet()) {
        if ("Validation".equals(category)) {
          continue;
        }
        SheetValue sheetValue = valuesByCategory.get(category);
        if (sheetValue == null || !SheetValue.Type.DOUBLE.equals(sheetValue.type())
            || sheetValue.doubleValue() == 0) {
          System.out.println("skipping value: " + sheetValue);
          continue;
        }
        stockModelBuilder
            .setAllocation(CategoryModel.create(category, 100 * sheetValue.doubleValue()));
      }
      stockModelByTicker.put(ticker, stockModelBuilder.build());
      System.out.println(stockModelByTicker.get(ticker));
    }
  }

  private Table<Map<String, SheetValue>, String, SheetValue> parseSheet(Sheet sheet,
                                                                        String... keyColumnNames) {
    Preconditions.checkArgument(keyColumnNames.length != 0, "keyColumnNames must have 1 key");
    Table<Map<String, SheetValue>, String, SheetValue> outputTable = HashBasedTable.create();
    Iterator<Row> rowIterator = sheet.iterator();
    if (!rowIterator.hasNext()) {
      return outputTable;
    }
    Map<Integer, String> columnNameByColumnIndex = new HashMap<>();
    Set<Integer> keyColumnIndexes = new HashSet<>();
    Set<String> keyColumnNamesSet = new HashSet<>();
    keyColumnNamesSet.addAll(Arrays.asList(keyColumnNames));
    Row row = rowIterator.next();
    for (Cell cell : row) {
      String columnName = extractString(cell, false);
      if (columnName == null) {
        System.out.println("continue due to columnName not being a string");
        continue;
      }
      if (keyColumnNamesSet.remove(columnName)) {
        keyColumnIndexes.add(cell.getColumnIndex());
      }
      columnNameByColumnIndex.put(cell.getColumnIndex(), columnName);
      System.out.println(String.format("Puttings %s => %s", cell.getColumnIndex(), columnName));
    }
    if (!keyColumnNamesSet.isEmpty()) {
      throw new IllegalArgumentException("Missing some key Column Names: " + keyColumnNamesSet);
    }
    while (rowIterator.hasNext()) {
      row = rowIterator.next();
      ImmutableMap.Builder<String, SheetValue> keyBuilder = ImmutableMap.builder();
      boolean hasFullKey = true;
      for (Integer keyColumnIndex : keyColumnIndexes) {
        Cell cell = row.getCell(keyColumnIndex);
        if (cell == null) {
          hasFullKey = false;
          System.out.println("Missing key value: "
                             + columnNameByColumnIndex.get(keyColumnIndex) + " in row " + row
              .getRowNum());
          break;
        }
        SheetValue keyValue = extractSheetValue(cell, false);
        if (keyValue == null) {
          hasFullKey = false;
          System.out.println("Missing key value: "
                             + columnNameByColumnIndex.get(keyColumnIndex) + " in row " + row
              .getRowNum());
          break;
        }
        keyBuilder.put(columnNameByColumnIndex.get(keyColumnIndex), keyValue);
      }
      if (!hasFullKey) {
        System.out.println("Skipping row because missing key");
        continue;
      }
      Map<String, SheetValue> key = keyBuilder.build();
      System.out.println(String.format("Running row %s", key));
      for (Cell cell : row) {
        if (keyColumnIndexes.contains(cell.getColumnIndex())) {
          // skip key cell
          continue;
        }
        SheetValue sheetValue = extractSheetValue(cell, false);
        if (sheetValue == null) {
          continue;
        }
        String columnName = columnNameByColumnIndex.get(cell.getColumnIndex());
        if (columnName != null) {
          outputTable.put(key, columnName, sheetValue);
          System.out.println(String.format("Puttings %s, %s, => %s", key, columnName, sheetValue));
        } else {
          System.out.println("continue due to no colum name!");
        }
      }
    }
    return outputTable;
  }

  private String extractString(Cell cell, boolean throwException) {
    switch (cell.getCellType()) {
      case Cell.CELL_TYPE_STRING:
        return cell.getRichStringCellValue().getString();
      default:
        if (throwException) {
          CellReference cellRef = new CellReference(cell);
          throw new IllegalArgumentException("cell is not a string for cell "
                                             + cellRef.formatAsString());
        } else {
          return null;
        }
    }
  }

  @Nullable
  private Double extractNumber(Cell cell, boolean throwException) {
    switch (cell.getCellType()) {
      case Cell.CELL_TYPE_NUMERIC:
        return cell.getNumericCellValue();
      default:
        if (throwException) {
          CellReference cellRef = new CellReference(cell);
          throw new IllegalArgumentException("cell is not a number for cell "
                                             + cellRef.formatAsString());
        } else {
          return null;
        }
    }
  }

  @Nullable
  private SheetValue extractSheetValue(Cell cell, boolean throwException) {
    switch (cell.getCellType()) {
      case Cell.CELL_TYPE_NUMERIC:
        return SheetValue.createSheetValue(cell.getNumericCellValue());
      case Cell.CELL_TYPE_STRING:
        return SheetValue.createSheetValue(cell.getRichStringCellValue().getString());
      default:
        if (throwException) {
          CellReference cellRef = new CellReference(cell);
          throw new IllegalArgumentException("cell is not a number or string for cell "
                                             + cellRef.formatAsString() + " " + cell.getCellType());
        } else {
          return null;
        }
    }
  }

  @Override
  public List<AccountModel> getAccounts() {
    return accountModel;
  }

  @Override
  public List<StockModel> getStocks() {
    return null;
  }

  @Override
  public List<CategoryGroupModel> getCategoryGroups() {
    return categoryGroups;
  }

}
