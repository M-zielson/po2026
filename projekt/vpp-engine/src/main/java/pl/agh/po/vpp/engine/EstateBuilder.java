package pl.agh.po.vpp.engine;

import pl.agh.po.vpp.domain.*;
import pl.agh.po.vpp.engine.asset.*;
import java.util.ArrayList;
import java.util.List;

/** Składa zakres M1 bez zmiany kontraktu konfiguracji. */
public final class EstateBuilder {
    public VirtualPowerPlant build(EstateConfig estate) {
        List<AssetConfig> grids = estate.assetsOfType(AssetType.GRID);
        if (grids.size() != 1) throw new InvalidEstateException("Wymagane dokładnie jedno przyłącze GRID");
        List<AbstractLoad> buildings = new ArrayList<>();
        List<AssetFlow> inactive = new ArrayList<>();
        var zone = estate.location().zone();
        for (var asset : estate.assets()) {
            if (!Double.isFinite(asset.pvPeakKw())) throw new InvalidEstateException("Niepoprawna moc PV: " + asset.id());
            SolarPanel pv = asset.type().isBuilding() && asset.pvPeakKw() > 0
                    ? new SolarPanel(asset.id(), asset.name(), asset.type(), asset.pvPeakKw()) : null;
            switch (asset.type()) {
                case APARTMENT_BLOCK -> buildings.add(new ApartmentBlock(asset.id(), asset.name(), zone, pv, asset.dwellings()));
                case HOUSE -> buildings.add(new House(asset.id(), asset.name(), zone, pv, asset.dwellings()));
                case SCHOOL -> buildings.add(new School(asset.id(), asset.name(), zone, pv));
                case WIND_TURBINE, BATTERY -> inactive.add(new AssetFlow(asset.id(), asset.type(), 0, 0));
                case GRID -> { }
            }
        }
        return new VirtualPowerPlant(buildings, grids.getFirst().id(), inactive);
    }
}
