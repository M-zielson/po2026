package pl.agh.po.vpp.app;

import pl.agh.po.vpp.domain.SimulationResult;
import pl.agh.po.vpp.domain.StepResult;

import java.util.Locale;

/**
 * Sumy energii za całą dobę [kWh] do podsumowania w konsoli.
 * Tymczasowe – w M2 zastąpi je {@code MetricsCalculator} z vpp-data (KPI, koszt, samowystarczalność).
 */
public record DaySummary(double productionKwh, double consumptionKwh, double importKwh, double exportKwh) {

    public static DaySummary of(SimulationResult result) {
        double production = 0;
        double consumption = 0;
        double imported = 0;
        double exported = 0;
        for (StepResult step : result.steps()) {
            production += step.energyKwh(step.productionKw());
            consumption += step.energyKwh(step.consumptionKw());
            imported += step.energyKwh(step.importKw());
            exported += step.energyKwh(step.exportKw());
        }
        return new DaySummary(production, consumption, imported, exported);
    }

    /** Dwie linie tekstu do wypisania w konsoli. */
    public String format() {
        return String.format(Locale.ROOT,
                "Produkcja: %9.1f kWh   Zużycie: %9.1f kWh%nImport:    %9.1f kWh   Eksport: %9.1f kWh",
                productionKwh, consumptionKwh, importKwh, exportKwh);
    }
}
