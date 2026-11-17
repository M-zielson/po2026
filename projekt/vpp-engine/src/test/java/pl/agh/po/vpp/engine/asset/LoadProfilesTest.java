package pl.agh.po.vpp.engine.asset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.agh.po.vpp.domain.*;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class LoadProfilesTest {
    private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");

    private Conditions at(String local, double temperature) {
        var t = LocalDateTime.parse(local).atZone(ZONE).toInstant();
        return new Conditions(t, new Weather(t, 1000, 0, temperature, 0), 0.5);
    }

    @Test
    void mieszkaniaMajaSzczytySkalowanieWeekendITemperature() {
        for (AbstractLoad load : List.of(new House("h", "", ZONE, null, 1),
                new ApartmentBlock("b", "", ZONE, null, 1))) {
            double night = load.demandKw(at("2026-11-13T02:00", 20));
            double morning = load.demandKw(at("2026-11-13T07:30", 20));
            double noon = load.demandKw(at("2026-11-13T12:00", 20));
            double evening = load.demandKw(at("2026-11-13T19:30", 20));
            assertThat(morning).isGreaterThan(night).isGreaterThan(noon);
            assertThat(evening).isGreaterThan(morning);
            assertThat(load.demandKw(at("2026-11-14T07:30", 20))).isLessThan(morning);
            assertThat(load.demandKw(at("2026-11-13T12:00", -10))).isGreaterThan(noon);
        }
        var c = at("2026-11-13T12:00", 0);
        assertThat(new ApartmentBlock("b", "", ZONE, null, 40).demandKw(c))
                .isCloseTo(40 * new ApartmentBlock("b", "", ZONE, null, 1).demandKw(c), within(1e-12));
        assertThat(new House("h", "", ZONE, null, 3).demandKw(c))
                .isCloseTo(3 * new House("h", "", ZONE, null, 1).demandKw(c), within(1e-12));
        assertThat(new House("h", "", ZONE, null, 0).demandKw(c)).isZero();
    }

    @ParameterizedTest
    @CsvSource({"2026-11-13T07:59,1", "2026-11-13T08:00,10", "2026-11-13T14:59,10",
            "2026-11-13T15:00,1", "2026-11-14T10:00,1", "2026-11-15T10:00,1"})
    void szkolaGranice(String local, double expected) {
        assertThat(new School("s", "", ZONE, null).demandKw(at(local, 20))).isEqualTo(expected);
    }

    @Test
    void strefaJestStrefaBudynku() {
        var c = at("2026-11-13T08:00", 20);
        assertThat(new School("s", "", ZoneId.of("UTC"), null).demandKw(c)).isEqualTo(1);
        assertThat(new School("s", "", ZONE, null).demandKw(c)).isEqualTo(10);
    }

    @Test
    void stanWylaczaPoborIPanele() {
        var pv = new SolarPanel("h", "", AssetType.HOUSE, 5);
        var load = new House("h", "", ZONE, pv, 1);
        var c = at("2026-11-13T12:00", 25);
        for (var state : List.of(AssetState.FAILED, AssetState.DISABLED)) {
            load.setState(state);
            assertThat(load.demandKw(c)).isZero();
            assertThat(load.roofProductionKw(c)).isZero();
        }
        load.setState(AssetState.RUNNING);
        assertThat(load.roofProductionKw(c)).isEqualTo(5);
        assertThat(load.demandKw(c)).isPositive();
        assertThat(new School("s", "", ZONE, null).roofProductionKw(c)).isZero();
    }

    @Test
    void parametryIMocGrzewcza() {
        var profile = new ResidentialProfile(1, 0, 0, 0.3, 20, 3);
        assertThat(profile.demandKw(LocalDateTime.parse("2026-11-13T12:00"), 0)).isEqualTo(3);
        assertThatThrownBy(() -> new House("h", "", ZONE, null, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResidentialProfile(Double.NaN, 0, 0, 0, 20, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new School("s", "", ZONE, null, 2, 1)).isInstanceOf(IllegalArgumentException.class);
        for (int hour = 0; hour < 24; hour++) {
            assertThat(profile.demandKw(LocalDateTime.of(2026, 11, 13, hour, 0), 50)).isNotNegative();
        }
    }
}
