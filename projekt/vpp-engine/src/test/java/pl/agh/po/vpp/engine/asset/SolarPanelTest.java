package pl.agh.po.vpp.engine.asset;

import org.junit.jupiter.api.Test;
import pl.agh.po.vpp.domain.Conditions;
import pl.agh.po.vpp.domain.Weather;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SolarPanelTest {

    private static final Instant NOON = Instant.parse("2026-06-21T10:00:00Z");

    private static Conditions conditions(double irradianceWm2, double tempC) {
        Weather weather = new Weather(NOON, irradianceWm2, 4.0, tempC, 10.0);
        return new Conditions(NOON, weather, 0.60);
    }

    @Test
    void wNocyProdukcjaWynosiZero() {
        SolarPanel panel = new SolarPanel("pv-1", "PV Blok 1", 30.0);

        assertThat(panel.powerKw(conditions(0.0, 14.0))).isZero();
    }

    @Test
    void wPoludnieWWarunkachStcDajeMocZnamionowa() {
        SolarPanel panel = new SolarPanel("pv-1", "PV Blok 1", 30.0);

        assertThat(panel.powerKw(conditions(1000.0, 25.0))).isCloseTo(30.0, within(1e-9));
    }

    @Test
    void wysokaTemperaturaObnizaProdukcje() {
        SolarPanel panel = new SolarPanel("pv-1", "PV Blok 1", 30.0);
        // Dla T = 45°C (+20°C względem STC) i gamma = -0.004 spadek wynosi 8%: 30 * 0.92 = 27.6 kW
        assertThat(panel.powerKw(conditions(1000.0, 45.0))).isCloseTo(27.6, within(1e-9));
    }

    @Test
    void przycinaMocDoZnamionowejWChlodnySlonecznyDzien() {
        SolarPanel panel = new SolarPanel("pv-1", "PV Blok 1", 30.0);
        // Surowa moc dla 1100 W/m2 i 0°C wynosiłaby 30 * 1.1 * 1.1 = 36.3 kW -> obcięte do 30.0 kW
        assertThat(panel.powerKw(conditions(1100.0, 0.0))).isEqualTo(30.0);
    }

    @Test
    void podczasAwariiZwracaZero() {
        SolarPanel panel = new SolarPanel("pv-1", "PV Blok 1", 30.0);
        panel.setState(AssetState.FAILED);

        assertThat(panel.powerKw(conditions(1000.0, 25.0))).isZero();
    }
}