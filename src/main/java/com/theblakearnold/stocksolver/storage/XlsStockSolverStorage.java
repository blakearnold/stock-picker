package com.theblakearnold.stocksolver.storage;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
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
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
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
import java.util.logging.Logger;

import javax.annotation.Nullable;

/**
 * Implements {@code StockSolverStorage} using a given XLS file.
 * <p>
 * The spreadsheet must be formatted the following way:
 * 3 Sheets - named "Stocks", "Allocations", and "Holdings".
 * The "Stocks" sheet is a list of all buyable stocks across each account.
 * Fixed Column names:
 * - Ticker: the ticker symbol for the stock
 * - Expense Ratio: The stocks expense ratio
 * - Validation: A column that is ignored, but is useful for ensuring all the categories add up to 1
 * Flex Columns:
 * - Any additional column
 *
 * <p>
 * Each sheet is setup to contain a header row, and data rows. The first row is always
 * the header row. Each sheet has a list of columns that, as a group, make the rows keys. This is
 * similar to the unique or key restraint in SQL databases. Every row must contain a value for the
 * key columns, and they must be unique within the sheet. See {@link #parseSheet} for the logic.
 *
 * Headers must be strings - they cannot be formulas that produce strings.
 *
 * Keys and values can only be Strings, Integers, and
 * Formulas that produce Strings and Integers. Currently supported formula operators:
 * https://poi.apache.org/spreadsheet/eval-devguide.html
 *
 * Any row that does not have a complete key is skipped.
 */
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
  private static final String EXPENSE_RATIO_COLUMN_NAME = "Expense Ratio";
  private static final String CATEGORY_COLUMN_NAME = "Category";
  private static final String GROUP_COLUMN_NAME = "Group";
  private static final String VALIDATION_COLUMN_NAME = "Validation";

  private final static Logger log = Logger.getLogger(XlsStockSolverStorage.class.getName());

  private final String filename;
  private ImmutableMap<String, StockModel> stockModelByTicker;
  private ImmutableList<AccountModel> accountModel;
  private ImmutableList<CategoryGroupModel> categoryGroups;
  private FormulaEvaluator evaluator;

  public XlsStockSolverStorage(String filename) {
    this.filename = filename;
  }

  public void load() throws IOException, InvalidFormatException {
    OPCPackage pkg = OPCPackage.open(filename);
    try {
      XSSFWorkbook wb = new XSSFWorkbook(pkg);
      evaluator = wb.getCreationHelper().createFormulaEvaluator();

      {
        Sheet sheet = wb.getSheet(STOCKS_SHEET_NAME);
        if (sheet == null) {
          throw new IllegalArgumentException("Input excel file is missing sheet: "
                                             + STOCKS_SHEET_NAME);
        }
        Table<Map<String, SheetValue>, String, SheetValue> stockTable =
            parseSheet(sheet, TICKER_COLUMN_NAME);
        stockModelByTicker = parseStockTable(stockTable);
      }

      {
        Sheet allocationsSheet = wb.getSheet(ALLOCATIONS_SHEET_NAME);
        if (allocationsSheet == null) {
          throw new IllegalArgumentException("Input excel file is missing sheet: "
                                             + ALLOCATIONS_SHEET_NAME);
        }
        Table<Map<String, SheetValue>, String, SheetValue> allocationsTable =
            parseSheet(allocationsSheet, CATEGORY_COLUMN_NAME);
        categoryGroups = parseAllocationsTable(allocationsTable);
        log.fine(categoryGroups.toString());
      }

      {
        Sheet holdingsSheet = wb.getSheet(HOLDINGS_SHEET_NAME);
        if (holdingsSheet == null) {
          throw new IllegalArgumentException("Input excel file is missing sheet: "
                                             + HOLDINGS_SHEET_NAME);
        }
        Table<Map<String, SheetValue>, String, SheetValue> holdingsTable =
            parseSheet(holdingsSheet, TICKER_COLUMN_NAME, ACCOUNT_COLUMN_NAME);
        accountModel = parseHoldingsTable(holdingsTable);
        log.fine(accountModel.toString());
      }
    } finally {
      try {
        Closeables.close(pkg, true);
      } catch (Throwable t) {
        log.severe("Mehh... ");
        t.printStackTrace();
      }
    }
  }

  private ImmutableList<AccountModel> parseHoldingsTable(
      Table<Map<String, SheetValue>, String, SheetValue> holdingsTable) {
    Map<String, AccountModel.Builder> accountModelBuilderByAccountName = new HashMap<>();
    Map<String, Double> valueByAccountName = new HashMap<>();
    for (Map<String, SheetValue> keyMap : holdingsTable.rowKeySet()) {
      SheetValue tickerSheetValue = keyMap.get(TICKER_COLUMN_NAME);
      String ticker = tickerSheetValue.getString();
      SheetValue accountSheetValue = keyMap.get(ACCOUNT_COLUMN_NAME);
      String accountName = accountSheetValue.getString();

      SheetValue holdingsSheetValue = holdingsTable.get(keyMap, CURRENT_VALUE_COLUMN_NAME);
      double tickerValue = 0;
      if (holdingsSheetValue == null) {
        log.fine("skipping adding value: " + holdingsSheetValue + " : " + ticker);
      } else {
        tickerValue = holdingsSheetValue.getDoubleWithParsing();
        if (tickerValue == 0) {
          log.fine("skipping adding value: " + holdingsSheetValue + " : " + ticker);
        } else {
          Double value = valueByAccountName.get(accountName);
          double newValue = value == null ? tickerValue : tickerValue + value;
          valueByAccountName.put(accountName, newValue);
        }
      }

      if (!stockModelByTicker.containsKey(ticker)) {
        log.warning("skipping adding ticker to account because not defined in stocks sheet: "
                    + ticker);
        continue;
      }

      double minValue = 0;
      SheetValue minValueSheetValue = holdingsTable.get(keyMap, MIN_VALUE_COLUMN_NAME);
      if (minValueSheetValue == null) {
        log.warning(String.format("skipping adding min value for ticker %s,"
                                  + " missing column: %s", ticker, MIN_VALUE_COLUMN_NAME));
      } else {

        Double minValueParsed = minValueSheetValue.getDoubleWithParsing();
        if (minValueParsed == null) {
          log.fine("skipping adding value: " + minValueParsed + " : " + ticker);
        } else {
          minValue = minValueParsed;
        }
      }
      boolean locked = false;
      SheetValue lockedSheetValue = holdingsTable.get(keyMap, LOCKED_COLUMN_NAME);
      if (lockedSheetValue == null) {
        log.fine("setting locked value to false: " + ticker);
        locked = false;
      } else {
        Double lockedValueParsed = lockedSheetValue.getDoubleWithParsing();
        // For now, have anything non zero in the locked column signify locked.
        if (lockedValueParsed == null || lockedValueParsed.doubleValue() == 0) {
          log.fine("setting locked value to false: " + ticker);
          locked = false;
        } else {
          log.info("setting locked value to true: " + accountName + " " + ticker);
          locked = true;
        }
      }
      AccountModel.Builder accountModelBuilder = accountModelBuilderByAccountName.get(accountName);
      if (accountModelBuilder == null) {
        accountModelBuilder = AccountModel.newBuilder().setName(accountName);
        accountModelBuilderByAccountName.put(accountName, accountModelBuilder);
      }
      accountModelBuilder.addStockHoldingModel(
          StockHoldingModel.create(stockModelByTicker.get(ticker), minValue, locked, tickerValue));

    }
    ImmutableList.Builder<AccountModel> accountModelBuilder = ImmutableList.builder();
    for (String accountName : accountModelBuilderByAccountName.keySet()) {
      Double accountValue = valueByAccountName.get(accountName);
      if (accountValue == null) {
        log.fine("skipping account with zero value: " + accountName);
        continue;
      }
      accountModelBuilderByAccountName.get(accountName).setValue(accountValue).build();
      accountModelBuilder.add(accountModelBuilderByAccountName.get(accountName)
                           .setValue(valueByAccountName.get(accountName)).build());
    }
    return accountModelBuilder.build();
  }

  private ImmutableList<CategoryGroupModel> parseAllocationsTable(
      Table<Map<String, SheetValue>, String, SheetValue> allocationsTable) {
    Map<String, CategoryGroupModel.Builder> cateogryGroupByGroupName = new HashMap<>();
    String defaultCategoryName = "Default";
    for (Map<String, SheetValue> keySet : allocationsTable.rowKeySet()) {
      SheetValue categorySheetValue = keySet.get(CATEGORY_COLUMN_NAME);
      String category = categorySheetValue.getString();
      if (VALIDATION_COLUMN_NAME.equals(category)) {
        continue;
      }
      SheetValue sheetValue = allocationsTable.get(keySet, PERCENT_COLUMN_NAME);
      if (sheetValue == null || !SheetValue.Type.DOUBLE.equals(sheetValue.type())
          || sheetValue.doubleValue() == 0) {
        log.fine("skipping value: " + sheetValue);
        continue;
      }
      SheetValue groupSheetValue = allocationsTable.get(keySet, GROUP_COLUMN_NAME);
      final String groupName;
      if (groupSheetValue == null) {
        log.fine("no value for group, adding to default: " + defaultCategoryName);
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
    ImmutableList.Builder<CategoryGroupModel> categoryGroupsBuilder = ImmutableList.builder();
    for (CategoryGroupModel.Builder categoryGroupModelBuilder : cateogryGroupByGroupName.values()) {
      categoryGroupsBuilder.add(categoryGroupModelBuilder.build());
    }
    return categoryGroupsBuilder.build();
  }

  private ImmutableMap<String, StockModel> parseStockTable(
      Table<Map<String, SheetValue>, String, SheetValue> stockTable) {
    ImmutableMap.Builder<String, StockModel> stockModelByTickerMapBuilder = ImmutableMap.builder();
    for (Map<String, SheetValue> keySet : stockTable.rowKeySet()) {
      SheetValue tickerSheetValue = keySet.get(TICKER_COLUMN_NAME);
      String ticker = tickerSheetValue.getString();
      StockModel.Builder stockModelBuilder = StockModel.newBuilder();
      stockModelBuilder.setTicker(ticker);
      Map<String, SheetValue> valuesByCategory = stockTable.row(keySet);
      SheetValue expenseRatioValue = valuesByCategory.get(EXPENSE_RATIO_COLUMN_NAME);
      Preconditions.checkState(expenseRatioValue != null,
          "Expense ratio missing for stock %s", ticker);
      Double expenseRatio = expenseRatioValue.getDoubleWithParsing();
      Preconditions.checkState(expenseRatio != null, "Expense ratio missing for stock %s", ticker);
      stockModelBuilder.setExpenseRatio(expenseRatio);
      for (String category : valuesByCategory.keySet()) {
        // Skip over known columns.
        if (ImmutableSet.of(VALIDATION_COLUMN_NAME, EXPENSE_RATIO_COLUMN_NAME).contains(category)) {
          continue;
        }
        SheetValue sheetValue = valuesByCategory.get(category);
        if (sheetValue == null || !SheetValue.Type.DOUBLE.equals(sheetValue.type())
            || sheetValue.doubleValue() == 0) {
          log.fine("skipping value: " + sheetValue + " category " + category);
          continue;
        }
        stockModelBuilder
            .setAllocation(CategoryModel.create(category, 100 * sheetValue.doubleValue()));
      }
      StockModel stockModel = stockModelBuilder.build();
      stockModelByTickerMapBuilder.put(ticker, stockModel);
      log.fine(stockModel.toString());
    }
    return stockModelByTickerMapBuilder.build();
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
    // Used for validating that all the keys are in the sheet.
    Set<String> keyColumnNamesSet = new HashSet<>();
    keyColumnNamesSet.addAll(Arrays.asList(keyColumnNames));
    Row row = rowIterator.next();
    // First row parsing
    for (Cell cell : row) {
      String columnName = extractString(cell, false);
      if (columnName == null) {
        log.fine("continue due to columnName not being a string");
        continue;
      }
      if (keyColumnNamesSet.remove(columnName)) {
        keyColumnIndexes.add(cell.getColumnIndex());
      }
      columnNameByColumnIndex.put(cell.getColumnIndex(), columnName);
      log.fine(String.format("Puttings %s => %s", cell.getColumnIndex(), columnName));
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
          log.fine("Missing key value: "
                   + columnNameByColumnIndex.get(keyColumnIndex) + " in row " + row
              .getRowNum());
          break;
        }
        SheetValue keyValue = extractSheetValue(cell, false);
        if (keyValue == null) {
          hasFullKey = false;
          log.fine("Missing key value: "
                   + columnNameByColumnIndex.get(keyColumnIndex) + " in row " + row
              .getRowNum());
          break;
        }
        keyBuilder.put(columnNameByColumnIndex.get(keyColumnIndex), keyValue);
      }
      if (!hasFullKey) {
        log.fine("Skipping row because missing key");
        continue;
      }
      Map<String, SheetValue> key = keyBuilder.build();
      log.fine(String.format("Running row %s", key));
      if (outputTable.containsRow(key)) {
        throw new IllegalArgumentException("2 exact keys found " + key);
      }
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
          log.fine(String.format("Puttings %s, %s, => %s", key, columnName, sheetValue));
        } else {
          log.severe("continue due to no colum name!");
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
    switch (evaluator.evaluateInCell(cell).getCellType()) {
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
    return ImmutableList.copyOf(accountModel);
  }

  @Override
  public List<CategoryGroupModel> getCategoryGroups() {
    return ImmutableList.copyOf(categoryGroups);
  }

}
