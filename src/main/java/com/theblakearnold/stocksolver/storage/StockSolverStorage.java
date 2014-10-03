package com.theblakearnold.stocksolver.storage;

import java.util.List;

import com.theblakearnold.stocksolver.model.AccountModel;
import com.theblakearnold.stocksolver.model.CategoryGroupModel;
import com.theblakearnold.stocksolver.model.StockModel;

public interface StockSolverStorage {
  List<AccountModel> getAccounts();

  List<StockModel> getStocks();

  List<CategoryGroupModel> getCategoryGroups();
}
