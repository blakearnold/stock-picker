package com.theblakearnold.stocksolver.storage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.theblakearnold.stocksolver.Category;
import com.theblakearnold.stocksolver.model.AccountModel;
import com.theblakearnold.stocksolver.model.CategoryGroupModel;
import com.theblakearnold.stocksolver.model.CategoryModel;
import com.theblakearnold.stocksolver.model.StockHoldingModel;
import com.theblakearnold.stocksolver.model.StockModel;

public class StaticStockSolverStorage implements StockSolverStorage {

  private static CategoryModel createCategoryModel(Category category, double percent) {
    return CategoryModel.create(category.name(), percent);
  }

  private static final ImmutableMap<String, StockModel> STOCK_MODELS;

  static {
    Map<String, StockModel> stockModels = new HashMap<>();
    stockModels.put("VBIIX", StockModel.newBuilder().setTicker("VBIIX")
        .setAllocation(createCategoryModel(Category.BONDS, 100)).build());
    stockModels.put("VIPSX", StockModel.newBuilder().setTicker("VIPSX")
        .setAllocation(createCategoryModel(Category.TIPS, 100)).build());
    stockModels.put("VSMAX", StockModel.newBuilder().setTicker("VSMAX")
        .setAllocation(createCategoryModel(Category.DOMESTIC_SMALL_CAP, 100)).build());
    stockModels.put("VTV", StockModel.newBuilder().setTicker("VTV")
        .setAllocation(createCategoryModel(Category.DOMESTIC_VALUE, 74))
        .setAllocation(createCategoryModel(Category.DOMESTIC_TOTAL, 26)).build());
    stockModels.put("VNQI", StockModel.newBuilder().setTicker("VNQI")
        .setAllocation(createCategoryModel(Category.REAL_ESTATE_INTL, 100)).build());
    stockModels.put("VTSAX", StockModel.newBuilder().setTicker("VTSAX")
        .setAllocation(createCategoryModel(Category.DOMESTIC_SMALL_CAP, 9))
        .setAllocation(createCategoryModel(Category.DOMESTIC_TOTAL, 47))
        .setAllocation(createCategoryModel(Category.DOMESTIC_VALUE, 44)).build());
    stockModels.put("VGSNX", StockModel.newBuilder().setTicker("VGSNX")
        .setAllocation(createCategoryModel(Category.REAL_ESTATE_DOM, 100)).build());


    stockModels.put("VBMPX", StockModel.newBuilder().setTicker("VBMPX")
        .setAllocation(createCategoryModel(Category.BONDS, 100)).build());
    stockModels.put("VEMPX", StockModel.newBuilder().setTicker("VEMPX")
        .setAllocation(createCategoryModel(Category.DOMESTIC_SMALL_CAP, 69))
        .setAllocation(createCategoryModel(Category.DOMESTIC_TOTAL, 31)).build());
    stockModels.put("VIIIX", StockModel.newBuilder().setTicker("VIIIX")
        .setAllocation(createCategoryModel(Category.DOMESTIC_VALUE, 50))
        .setAllocation(createCategoryModel(Category.DOMESTIC_TOTAL, 50)).build());
    stockModels.put("VTPSX", StockModel.newBuilder().setTicker("VTPSX")
        .setAllocation(createCategoryModel(Category.FOREIGN_VALUE, 52))
        .setAllocation(createCategoryModel(Category.FOREIGN_TOTAL, 48)).build());
    stockModels.put("SCHE", StockModel.newBuilder().setTicker("SCHE")
        .setAllocation(createCategoryModel(Category.EMERGING_MARKETS, 100)).build());
    stockModels.put("SCHG", StockModel.newBuilder().setTicker("SCHG")
        .setAllocation(createCategoryModel(Category.DOMESTIC_VALUE, 21))
        .setAllocation(createCategoryModel(Category.DOMESTIC_TOTAL, 79)).build());
    stockModels.put("SCHF", StockModel.newBuilder().setTicker("SCHF")
        .setAllocation(createCategoryModel(Category.FOREIGN_VALUE, 55))
        .setAllocation(createCategoryModel(Category.FOREIGN_TOTAL, 45)).build());
    stockModels.put("SCHC", StockModel.newBuilder().setTicker("SCHC")
        .setAllocation(createCategoryModel(Category.FOREIGN_SMALL_CAP, 100)).build());
    stockModels.put("SCHV", StockModel.newBuilder().setTicker("SCHV")
        .setAllocation(createCategoryModel(Category.DOMESTIC_VALUE, 100)).build());
    stockModels.put("SCHA", StockModel.newBuilder().setTicker("SCHA")
        .setAllocation(createCategoryModel(Category.DOMESTIC_SMALL_CAP, 100)).build());
    stockModels.put("SCHX", StockModel.newBuilder().setTicker("SCHX")
        .setAllocation(createCategoryModel(Category.DOMESTIC_VALUE, 50))
        .setAllocation(createCategoryModel(Category.DOMESTIC_TOTAL, 50)).build());
    stockModels.put("SFNNX", StockModel.newBuilder().setTicker("SFNNX")
        .setAllocation(createCategoryModel(Category.FOREIGN_VALUE, 68))
        .setAllocation(createCategoryModel(Category.FOREIGN_TOTAL, 32)).build());
    stockModels.put("SCHP", StockModel.newBuilder().setTicker("SCHP")
        .setAllocation(createCategoryModel(Category.TIPS, 100)).build());
    stockModels.put("VIIX", StockModel.newBuilder().setTicker("VIIX")
        .setAllocation(createCategoryModel(Category.DOMESTIC_TOTAL, 50))
        .setAllocation(createCategoryModel(Category.DOMESTIC_VALUE, 50)).build());
    stockModels.put("PFORX", StockModel.newBuilder().setTicker("PFORX")
        .setAllocation(createCategoryModel(Category.BONDS_INTL, 100)).build());
    STOCK_MODELS = ImmutableMap.copyOf(stockModels);
  }

  private static final ImmutableList<AccountModel> ACCOUNT_MODELS = ImmutableList.of(
      AccountModel.newBuilder()
          .setName("IRA")
          .setValue(1000.22)
          .addStockHoldingModel(StockHoldingModel.create(STOCK_MODELS.get("VBIIX"), 0))
          .addStockHoldingModel(StockHoldingModel.create(STOCK_MODELS.get("VIPSX"), 0))
          .addStockHoldingModel(StockHoldingModel.create(STOCK_MODELS.get("VSMAX"), 0))
          .addStockHoldingModel(StockHoldingModel.create(STOCK_MODELS.get("VTV"), 0))
          .addStockHoldingModel(StockHoldingModel.create(STOCK_MODELS.get("VNQI"), 0))
          .addStockHoldingModel(StockHoldingModel.create(STOCK_MODELS.get("VTSAX"), 0))
          .build(),

      AccountModel.newBuilder()
          .setName("G401k")
          .setValue(1000.22)
          .addStockHoldingModel(StockHoldingModel.create(STOCK_MODELS.get("VGSNX"), 0))
          .addStockHoldingModel(StockHoldingModel.create(STOCK_MODELS.get("VBMPX"), 0))
          .addStockHoldingModel(StockHoldingModel.create(STOCK_MODELS.get("VEMPX"), 0))
          .addStockHoldingModel(StockHoldingModel.create(STOCK_MODELS.get("VIIIX"), 0))
          .addStockHoldingModel(StockHoldingModel.create(STOCK_MODELS.get("VTPSX"), 0))
          .build(),

      AccountModel.newBuilder()
          .setName("Schwab")
          .setValue(1000.22)
          .addStockHoldingModel(StockHoldingModel.create(STOCK_MODELS.get("SCHE"), 0))
          .addStockHoldingModel(StockHoldingModel.create(STOCK_MODELS.get("SCHG"), 0))
          .addStockHoldingModel(StockHoldingModel.create(STOCK_MODELS.get("SCHF"), 0))
          .addStockHoldingModel(StockHoldingModel.create(STOCK_MODELS.get("SCHC"), 0))
          .build(),

      AccountModel.newBuilder()
          .setName("HSA")
          .setValue(1000.22)
          .addStockHoldingModel(StockHoldingModel.create(STOCK_MODELS.get("VIIX"), 0))
          .addStockHoldingModel(StockHoldingModel.create(STOCK_MODELS.get("PFORX"), 0))
          .build());

  private static final ImmutableList<CategoryGroupModel> CATEGORY_GROUP_MODELS = ImmutableList.of(
      CategoryGroupModel.newBuilder().setName("tips-bonds")
          .addCategory(CategoryModel.create(Category.TIPS.name(), 6.0))
          .addCategory(CategoryModel.create(Category.BONDS.name(), 4.0))
          .addCategory(CategoryModel.create(Category.BONDS_INTL.name(), 4.0)).build(),

      CategoryGroupModel.newBuilder().setName("real-estate")
          .addCategory(CategoryModel.create(Category.REAL_ESTATE_DOM.name(), 6.0))
          .addCategory(CategoryModel.create(Category.REAL_ESTATE_INTL.name(), 6.0)).build(),

      CategoryGroupModel.newBuilder().setName("emerging-markets")
          .addCategory(CategoryModel.create(Category.EMERGING_MARKETS.name(), 17.0)).build(),

      CategoryGroupModel.newBuilder().setName("domestic")
          .addCategory(CategoryModel.create(Category.DOMESTIC_TOTAL.name(), 14.0))
          .addCategory(CategoryModel.create(Category.DOMESTIC_VALUE.name(), 14.0))
          .addCategory(CategoryModel.create(Category.DOMESTIC_SMALL_CAP.name(), 7.00)).build(),
      CategoryGroupModel.newBuilder().setName("domestic")
          .addCategory(CategoryModel.create(Category.FOREIGN_TOTAL.name(), 9.0))
          .addCategory(CategoryModel.create(Category.FOREIGN_VALUE.name(), 9.0))
          .addCategory(CategoryModel.create(Category.FOREIGN_SMALL_CAP.name(), 4.0)).build());

	static {
		double total = 0;
		for (CategoryGroupModel categoryGroupModel : CATEGORY_GROUP_MODELS) {
			for (CategoryModel categoryModel : categoryGroupModel.categories()) {
				total += categoryModel.percent();
			}
		}
		Preconditions.checkArgument(total == 100, "Category Percentages dont sum to 100 %s", total);
	}


	@Override
	public List<AccountModel> getAccounts() {
		return ACCOUNT_MODELS;
	}

	@Override
	public List<StockModel> getStocks() {
		return null;
	}

	@Override
	public List<CategoryGroupModel> getCategoryGroups() {
    return CATEGORY_GROUP_MODELS;
  }
}
