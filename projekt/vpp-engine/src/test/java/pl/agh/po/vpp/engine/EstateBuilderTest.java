package pl.agh.po.vpp.engine;

import org.junit.jupiter.api.Test;
import pl.agh.po.vpp.domain.*;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class EstateBuilderTest {
    private final Location location = new Location(50, 20, ZoneId.of("UTC"));
    private AssetConfig asset(String id, AssetType type, double pv, int dwellings) {
        return new AssetConfig(id, type, id, pv, 5, type == AssetType.BATTERY ? 10 : 0, dwellings, null);
    }

    @Test
    void wszystkieTypyWspolneIdIZeroweZasobyM2() {
        var estate = new EstateConfig("test", location, List.of(
                asset("b", AssetType.APARTMENT_BLOCK, 30, 40), asset("h", AssetType.HOUSE, 5, 1),
                asset("s", AssetType.SCHOOL, 20, 0), asset("w", AssetType.WIND_TURBINE, 0, 0),
                asset("bat", AssetType.BATTERY, 0, 0), asset("g", AssetType.GRID, 0, 0)));
        var t = Instant.parse("2026-11-13T08:00:00Z");
        var step = new EstateBuilder().build(estate).step(new Conditions(t, new Weather(t, 1000, 12, 25, 0), 1), 1);
        assertThat(step.productionKw()).isEqualTo(55);
        assertThat(step.flows()).extracting(AssetFlow::assetId).containsExactlyInAnyOrder("b", "h", "s", "w", "bat", "g");
        assertThat(step.flowOf("s").orElseThrow().consumptionKw()).isEqualTo(10);
        for (var id : List.of("w", "bat")) {
            assertThat(step.flowOf(id).orElseThrow().productionKw()).isZero();
            assertThat(step.flowOf(id).orElseThrow().consumptionKw()).isZero();
        }
        EnergyBalanceValidator.validate(step);
    }

    @Test
    void wymagaJednegoGrid() {
        for (var assets : List.of(List.of(asset("h", AssetType.HOUSE, 0, 1)),
                List.of(asset("g1", AssetType.GRID, 0, 0), asset("g2", AssetType.GRID, 0, 0)))) {
            assertThatThrownBy(() -> new EstateBuilder().build(new EstateConfig("test", location, assets)))
                    .isInstanceOf(InvalidEstateException.class);
        }
    }
}
