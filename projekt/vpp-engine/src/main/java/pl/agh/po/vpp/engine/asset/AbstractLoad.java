package pl.agh.po.vpp.engine.asset;

import pl.agh.po.vpp.domain.AssetType;
import pl.agh.po.vpp.domain.Conditions;
import pl.agh.po.vpp.domain.Load;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Klasa bazowa dla odbiorców energii (budynków osiedla: blok, dom, szkoła).
 */
public abstract class AbstractLoad implements Load {

    public static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Warsaw");

    private final String id;
    private final String name;
    private final AssetType type;
    private final ZoneId zone;
    private final SolarPanel roofPv;
    private AssetState state = AssetState.RUNNING;

    protected AbstractLoad(String id, String name, AssetType type, ZoneId zone, SolarPanel roofPv) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Identyfikator odbiorcy nie może być pusty");
        }
        if (type == null || !type.isBuilding()) {
            throw new IllegalArgumentException("Odbiorca musi być budynkiem: " + type);
        }
        if (zone == null) {
            throw new IllegalArgumentException("Strefa czasowa nie może być null");
        }
        this.id = id;
        this.name = (name == null || name.isBlank()) ? id : name;
        this.type = type;
        this.zone = zone;
        this.roofPv = roofPv;
    }

    @Override
    public final double demandKw(Conditions conditions) {
        if (conditions == null) {
            throw new IllegalArgumentException("Conditions nie może być null");
        }
        if (state != AssetState.RUNNING) {
            return 0.0;
        }
        LocalDateTime localTime = LocalDateTime.ofInstant(conditions.time(), zone);
        return Math.max(0.0, profileDemandKw(localTime, conditions));
    }

    /** Produkcja z paneli fotowoltaicznych na dachu budynku [kW]. */
    public double roofProductionKw(Conditions conditions) {
        if (roofPv == null || state != AssetState.RUNNING) {
            return 0.0;
        }
        return roofPv.powerKw(conditions);
    }

    public Optional<SolarPanel> roofPv() {
        return Optional.ofNullable(roofPv);
    }

    protected abstract double profileDemandKw(LocalDateTime localTime, Conditions conditions);

    @Override
    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    @Override
    public AssetType type() {
        return type;
    }

    public ZoneId zone() {
        return zone;
    }

    public AssetState state() {
        return state;
    }

    public void setState(AssetState state) {
        if (state == null) {
            throw new IllegalArgumentException("Stan zasobu nie może być null");
        }
        this.state = state;
        if (roofPv != null) {
            roofPv.setState(state);
        }
    }
}
