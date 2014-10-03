package com.theblakearnold.stocksolver;

import javax.inject.Inject;

import com.google.ortools.linearsolver.MPSolver.OptimizationProblemType;

import dagger.ObjectGraph;

public class StockSolverMain {

  private final StockSolver stockSolver;

  @Inject
  public StockSolverMain(StockSolver stockSolver) {
    this.stockSolver = stockSolver;
  }

	public void run() {
		// System.out.println("---- Linear programming example with CLP ----");
		// stockSolver.runLinearProgrammingExample("CLP_LINEAR_PROGRAMMING");
		System.out.println("---- Linear programming example with CLP ----");
		// stockSolver.runLinearProgrammingExample("SCIP_MIXED_INTEGER_PROGRAMMING");
		stockSolver.optimizeWiggleRoom(OptimizationProblemType.CLP_LINEAR_PROGRAMMING, .01);
	}

	public static void main(String[] args) throws Exception {
		ObjectGraph objectGraph = ObjectGraph.create(new StockSolverModule());
		StockSolverMain stockSolverMain = objectGraph.get(StockSolverMain.class);
		stockSolverMain.run();
	}
}
