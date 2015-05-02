package com.theblakearnold.stocksolver.storage;

import com.theblakearnold.stocksolver.model.AccountModel;
import com.theblakearnold.stocksolver.model.CategoryGroupModel;
import com.theblakearnold.stocksolver.model.StockModel;

import java.util.List;

public interface StockSolverStorage {

  List<AccountModel> getAccounts();

  List<CategoryGroupModel> getCategoryGroups();
}
