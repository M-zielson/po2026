package pl.agh.po.vpp.engine;

import pl.agh.po.vpp.domain.*;
import pl.agh.po.vpp.engine.asset.AbstractLoad;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Bilans M1: budynki z PV i sieć. Magazyn oraz wiatrak mają zerowe przepływy. */
public final class VirtualPowerPlant {
    private final List<AbstractLoad> buildings;
    private final String gridId;
    private final List<AssetFlow> inactiveFlows;

    public VirtualPowerPlant(List<AbstractLoad> buildings, String gridId, List<AssetFlow> inactiveFlows) {
        this.buildings = List.copyOf(buildings);
        this.inactiveFlows = List.copyOf(inactiveFlows);
        if (gridId == null || gridId.isBlank()) throw new IllegalArgumentException("Brak id GRID");
        this.gridId = gridId;
        var ids = new HashSet<String>();
        ids.add(gridId);
        for (var building : buildings) {
            if (!ids.add(building.id())) throw new IllegalArgumentException("Powtórzone id: " + building.id());
        }
        for (var flow : inactiveFlows) {
            if ((flow.type() != AssetType.BATTERY && flow.type() != AssetType.WIND_TURBINE)
                    || flow.productionKw() != 0 || flow.consumptionKw() != 0 || !ids.add(flow.assetId())) {
                throw new IllegalArgumentException("Niepoprawny nieaktywny zasób M1: " + flow.assetId());
            }
        }
    }

    public StepResult step(Conditions conditions, double stepHours) {
        Objects.requireNonNull(conditions);
        double seconds = stepHours * 3600;
        if (!Double.isFinite(seconds) || seconds < 1 || seconds > Long.MAX_VALUE
                || Math.abs(seconds - Math.rint(seconds)) > 1e-8) {
            throw new IllegalArgumentException("Krok musi być dodatnią całkowitą liczbą sekund");
        }
        List<AssetFlow> flows = new ArrayList<>();
        double production = 0;
        double consumption = 0;
        for (var building : buildings) {
            double pv = building.roofProductionKw(conditions);
            double load = building.demandKw(conditions);
            flows.add(new AssetFlow(building.id(), building.type(), pv, load));
            production += pv;
            consumption += load;
        }
        flows.addAll(inactiveFlows);
        double grid = consumption - production;
        flows.add(new AssetFlow(gridId, AssetType.GRID, Math.max(grid, 0), Math.max(-grid, 0)));
        var result = new StepResult(conditions.time(), Duration.ofSeconds(Math.round(seconds)), conditions,
                flows, production, consumption, 0, grid, 0);
        EnergyBalanceValidator.validate(result);
        return result;
    }
}
