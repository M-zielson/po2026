package pl.agh.po.vpp.app;

import pl.agh.po.vpp.domain.SimulationListener;
import pl.agh.po.vpp.domain.SimulationResult;
import pl.agh.po.vpp.domain.StepResult;

import java.io.PrintStream;

/**
 * Słuchacz symulacji (wzorzec Observer) rysujący pasek postępu w konsoli:
 * {@code [###############---------------] 48/96 kroków}.
 * Linia jest nadpisywana znakiem powrotu karetki, a po końcu symulacji kończona znakiem nowej linii.
 */
public class ConsoleProgressListener implements SimulationListener {

    private static final int BAR_WIDTH = 30;

    private final PrintStream out;
    private final int totalSteps;
    private int doneSteps;

    public ConsoleProgressListener(PrintStream out, int totalSteps) {
        if (totalSteps <= 0) {
            throw new IllegalArgumentException("Liczba kroków musi być dodatnia: " + totalSteps);
        }
        this.out = out;
        this.totalSteps = totalSteps;
    }

    @Override
    public void onStep(StepResult step) {
        doneSteps++;
        int filled = BAR_WIDTH * doneSteps / totalSteps;
        out.print("\r[" + "#".repeat(filled) + "-".repeat(BAR_WIDTH - filled) + "] "
                + doneSteps + "/" + totalSteps + " kroków");
    }

    @Override
    public void onFinished(SimulationResult result) {
        out.println();
    }
}
