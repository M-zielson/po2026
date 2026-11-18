package pl.agh.po.vpp.data.metrics;

/**
 * Metryki (KPI) jednej doby symulacji: karty w panelu operatora i podsumowanie w konsoli.
 * Energia w kWh, moc w kW, pieniądze w PLN. Wskaźniki jako ułamek 0–1;
 * {@code null}, gdy nie da się ich policzyć (dzielenie przez zero).
 *
 * @param productionKwh       produkcja paneli i wiatraka
 * @param consumptionKwh      zużycie budynków
 * @param importKwh           energia kupiona z sieci
 * @param exportKwh           energia sprzedana do sieci
 * @param batteryChargeKwh    energia wprowadzona do magazynu
 * @param batteryDischargeKwh energia oddana przez magazyn
 * @param selfSufficiency     samowystarczalność: część zużycia pokryta bez importu; null bez zużycia
 * @param selfConsumption     autokonsumpcja: część produkcji zużyta w osiedlu; null bez produkcji
 * @param importCostPln       koszt importu
 * @param exportRevenuePln    przychód z eksportu
 * @param netCostPln          koszt dnia = koszt importu − przychód z eksportu (ujemny = osiedle zarobiło)
 * @param peakImportKw        największy chwilowy import
 * @param peakExportKw        największy chwilowy eksport
 */
public record DayMetrics(
        double productionKwh,
        double consumptionKwh,
        double importKwh,
        double exportKwh,
        double batteryChargeKwh,
        double batteryDischargeKwh,
        Double selfSufficiency,
        Double selfConsumption,
        double importCostPln,
        double exportRevenuePln,
        double netCostPln,
        double peakImportKw,
        double peakExportKw) {

    public DayMetrics {
        requireNonNegative(productionKwh, "productionKwh");
        requireNonNegative(consumptionKwh, "consumptionKwh");
        requireNonNegative(importKwh, "importKwh");
        requireNonNegative(exportKwh, "exportKwh");
        requireNonNegative(batteryChargeKwh, "batteryChargeKwh");
        requireNonNegative(batteryDischargeKwh, "batteryDischargeKwh");
        requireNonNegative(peakImportKw, "peakImportKw");
        requireNonNegative(peakExportKw, "peakExportKw");
        requireFraction(selfSufficiency, "selfSufficiency");
        requireFraction(selfConsumption, "selfConsumption");
        if (!Double.isFinite(importCostPln) || !Double.isFinite(exportRevenuePln) || !Double.isFinite(netCostPln)) {
            throw new IllegalArgumentException("Kwoty muszą być skończonymi liczbami");
        }
    }

    private static void requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(name + " musi być nieujemną liczbą: " + value);
        }
    }

    private static void requireFraction(Double value, String name) {
        if (value != null && (!Double.isFinite(value) || value < 0 || value > 1)) {
            throw new IllegalArgumentException(name + " musi być w zakresie 0–1: " + value);
        }
    }
}
