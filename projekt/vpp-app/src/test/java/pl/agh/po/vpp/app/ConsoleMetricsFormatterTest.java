package pl.agh.po.vpp.app;

import org.junit.jupiter.api.Test;
import pl.agh.po.vpp.data.metrics.DayMetrics;

import static org.assertj.core.api.Assertions.assertThat;

class ConsoleMetricsFormatterTest {

    private static DayMetrics metrics(Double selfSufficiency, Double selfConsumption) {
        return new DayMetrics(1175.24, 2016.0, 1118.66, 277.95, 0, 0,
                selfSufficiency, selfConsumption, 689.1, 76.7, 612.4, 120, 48);
    }

    @Test
    void wypisujeEnergieWskaznikiIKosztZKropkaDziesietna() {
        String text = ConsoleMetricsFormatter.format(metrics(0.4451, 0.7635));

        assertThat(text).contains("1175.2 kWh", "2016.0 kWh", "1118.7 kWh", "278.0 kWh");
        assertThat(text).contains("Samowystarczalność: 44.5%", "Autokonsumpcja: 76.4%");
        assertThat(text).contains("Koszt dnia: 612.40 PLN (import 689.10 − eksport 76.70)");
    }

    @Test
    void brakujacyWskaznikToKreska() {
        String text = ConsoleMetricsFormatter.format(metrics(0.0, null));

        assertThat(text).contains("Samowystarczalność: 0.0%", "Autokonsumpcja: —");
    }
}
