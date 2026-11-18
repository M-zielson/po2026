package pl.agh.po.vpp.data.metrics;

import pl.agh.po.vpp.domain.SimulationResult;
import pl.agh.po.vpp.domain.StepResult;

/**
 * Liczy metryki doby ({@link DayMetrics}) z wyników symulacji.
 * Energia kroku = moc × długość kroku; koszty z ceny w danym kroku.
 *
 * <p>Kontrakt ma jedną cenę energii na krok. Przychód z eksportu to
 * {@code eksport × cena × exportPriceFactor}: 1,0 oznacza sprzedaż po cenie zakupu,
 * 0,5 – za połowę (bliżej realiów sprzedaży energii przez prosumenta).
 */
public class MetricsCalculator {

    public static final double DEFAULT_EXPORT_PRICE_FACTOR = 1.0;

    /** Poniżej tej energii mianownik wskaźnika traktujemy jak zero (wskaźnik = brak wartości). */
    private static final double EPSILON_KWH = 1e-9;

    private final double exportPriceFactor;

    public MetricsCalculator() {
        this(DEFAULT_EXPORT_PRICE_FACTOR);
    }

    public MetricsCalculator(double exportPriceFactor) {
        if (!Double.isFinite(exportPriceFactor) || exportPriceFactor < 0) {
            throw new IllegalArgumentException("Współczynnik ceny sprzedaży musi być nieujemny: " + exportPriceFactor);
        }
        this.exportPriceFactor = exportPriceFactor;
    }

    public double exportPriceFactor() {
        return exportPriceFactor;
    }

    public DayMetrics calculate(SimulationResult result) {
        double production = 0;
        double consumption = 0;
        double imported = 0;
        double exported = 0;
        double charged = 0;
        double discharged = 0;
        double importCost = 0;
        double exportRevenue = 0;
        double peakImport = 0;
        double peakExport = 0;

        for (StepResult step : result.steps()) {
            double price = step.conditions().pricePlnPerKwh();
            double importKwh = step.energyKwh(step.importKw());
            double exportKwh = step.energyKwh(step.exportKw());

            production += step.energyKwh(step.productionKw());
            consumption += step.energyKwh(step.consumptionKw());
            imported += importKwh;
            exported += exportKwh;
            charged += step.energyKwh(Math.max(step.batteryKw(), 0));
            discharged += step.energyKwh(Math.max(-step.batteryKw(), 0));
            importCost += importKwh * price;
            exportRevenue += exportKwh * price * exportPriceFactor;
            peakImport = Math.max(peakImport, step.importKw());
            peakExport = Math.max(peakExport, step.exportKw());
        }

        return new DayMetrics(production, consumption, imported, exported, charged, discharged,
                fraction(consumption - imported, consumption),
                fraction(production - exported, production),
                importCost, exportRevenue, importCost - exportRevenue,
                peakImport, peakExport);
    }

    /**
     * Wskaźnik {@code part / whole} przycięty do 0–1, albo null, gdy {@code whole} ≈ 0.
     * Przycięcie jest potrzebne, bo np. ładowanie magazynu z sieci daje import większy od zużycia.
     */
    private static Double fraction(double part, double whole) {
        if (whole < EPSILON_KWH) {
            return null;
        }
        return Math.clamp(part / whole, 0.0, 1.0);
    }
}
