package pl.agh.po.vpp.engine;

import pl.agh.po.vpp.domain.*;
import java.util.HashSet;

/** Sprawdza energię i zgodność przepływów z agregatami, bez zaliczania GRID do OZE. */
public final class EnergyBalanceValidator {
    public static final double TOLERANCE_KWH = 1e-6;

    private EnergyBalanceValidator() { }

    public static void validate(StepResult step) {
        double hours = step.stepHours();
        if (!Double.isFinite(hours) || hours <= 0 || step.step().getNano() != 0
                || !step.time().equals(step.conditions().time())) fail("Niepoprawny czas kroku");
        nonnegative(step.productionKw());
        nonnegative(step.consumptionKw());
        nonnegative(step.batterySocKwh());
        double production = 0, consumption = 0, battery = 0, grid = 0;
        int grids = 0;
        var ids = new HashSet<String>();
        for (var flow : step.flows()) {
            if (!ids.add(flow.assetId())) fail("Powtórzony przepływ " + flow.assetId());
            nonnegative(flow.productionKw());
            nonnegative(flow.consumptionKw());
            switch (flow.type()) {
                case GRID -> { grids++; grid += flow.netKw(); }
                case BATTERY -> battery -= flow.netKw();
                default -> { production += flow.productionKw(); consumption += flow.consumptionKw(); }
            }
        }
        if (grids != 1) fail("Wymagany dokładnie jeden przepływ GRID");
        close(step.balanceErrorKw(), hours);
        close(production - step.productionKw(), hours);
        close(consumption - step.consumptionKw(), hours);
        close(grid - step.gridKw(), hours);
        close(battery - step.batteryKw(), hours);
    }

    private static void nonnegative(double value) {
        if (!Double.isFinite(value) || value < 0) fail("Niepoprawna moc lub energia");
    }

    private static void close(double differenceKw, double hours) {
        if (!Double.isFinite(differenceKw) || Math.abs(differenceKw * hours) >= TOLERANCE_KWH) {
            fail("Niezgodny bilans lub przepływy zasobów");
        }
    }

    private static void fail(String message) {
        throw new IllegalStateException(message);
    }
}
