package com.theblakearnold.stocksolver;

import com.theblakearnold.stocksolver.storage.StockSolverStorage;
import com.theblakearnold.stocksolver.storage.XlsStockSolverStorage;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;

import java.io.IOException;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module(
    injects = {
        InputValidator.class,
        StockSolverMain.class,
        StockSolver.class,
    }
)
public class StockSolverModule {

  @Provides
  @Singleton
  StockSolverStorage provideStockSolverStorage() {
    XlsStockSolverStorage xls = new XlsStockSolverStorage("solverTemplate.xlsx");
    try {
      xls.load();
    } catch (InvalidFormatException | IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return xls;
  }

}
