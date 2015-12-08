package com.theblakearnold.stocksolver;

import com.google.ortools.linearsolver.MPSolver.OptimizationProblemType;

import javax.inject.Inject;

import dagger.ObjectGraph;

public class StockSolverMain {

  private final StockSolver stockSolver;
  private final InputValidator validator;

  @Inject
  public StockSolverMain(StockSolver stockSolver, InputValidator validator) {
    this.stockSolver = stockSolver;
    this.validator = validator;
  }

  public void run() {
    System.out.println("Current Value:");
    stockSolver.printCurrentPercentage();
    validator.validate();
    System.out.println("\n\n\n\n\n\n");
    System.out.println("---- Linear programming example with CLP ----");
    stockSolver.optimizeWiggleRoomAdvanced(OptimizationProblemType.CLP_LINEAR_PROGRAMMING, .01);
  }

  public static void main(String[] args) throws Exception {
    ObjectGraph objectGraph = ObjectGraph.create(new StockSolverModule());
    StockSolverMain stockSolverMain = objectGraph.get(StockSolverMain.class);
    stockSolverMain.run();
  }
}
