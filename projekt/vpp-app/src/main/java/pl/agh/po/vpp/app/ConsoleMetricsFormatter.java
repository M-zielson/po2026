package pl.agh.po.vpp.app;

import pl.agh.po.vpp.data.metrics.DayMetrics;

import java.util.Locale;

/**
 * Podsumowanie metryk doby do wypisania w konsoli. Liczy je {@code MetricsCalculator} z vpp-data;
 * ta klasa odpowiada tylko za wygląd tekstu (kropka dziesiętna, „—” dla brakującego wskaźnika).
 */
public final class ConsoleMetricsFormatter {

    private ConsoleMetricsFormatter() {
    }

    public static String format(DayMetrics m) {
        return String.format(Locale.ROOT,
                "Produkcja: %9.1f kWh   Zużycie: %9.1f kWh%n"
                        + "Import:    %9.1f kWh   Eksport: %9.1f kWh%n"
                        + "Samowystarczalność: %s   Autokonsumpcja: %s%n"
                        + "Koszt dnia: %.2f PLN (import %.2f − eksport %.2f)",
                m.productionKwh(), m.consumptionKwh(), m.importKwh(), m.exportKwh(),
                percent(m.selfSufficiency()), percent(m.selfConsumption()),
                m.netCostPln(), m.importCostPln(), m.exportRevenuePln());
    }

    private static String percent(Double fraction) {
        return fraction == null ? "—" : String.format(Locale.ROOT, "%.1f%%", 100 * fraction);
    }
}
