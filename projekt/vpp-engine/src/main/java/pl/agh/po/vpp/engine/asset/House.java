package pl.agh.po.vpp.engine.asset;

import pl.agh.po.vpp.domain.AssetType;
import pl.agh.po.vpp.domain.Conditions;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

/** Dom korzysta z tego samego modelu aktywności, ale innych parametrów cieplnych. */
public final class House extends AbstractLoad {
    private final int dwellings;
    private final ResidentialProfile profile;

    public House(String id, String name, ZoneId zone, SolarPanel roofPv, int dwellings) {
        this(id, name, zone, roofPv, dwellings, ResidentialProfile.HOUSE);
    }

    public House(String id, String name, ZoneId zone, SolarPanel roofPv,
                 int dwellings, ResidentialProfile profile) {
        super(id, name, AssetType.HOUSE, zone, roofPv);
        if (dwellings < 0) throw new IllegalArgumentException("Liczba lokali nie może być ujemna");
        this.dwellings = dwellings;
        this.profile = Objects.requireNonNull(profile);
    }

    @Override
    protected double profileDemandKw(LocalDateTime time, Conditions conditions) {
        return dwellings * profile.demandKw(time, conditions.weather().temperatureC());
    }
}
