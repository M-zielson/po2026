package pl.agh.po.vpp.engine.asset;

import pl.agh.po.vpp.domain.AssetType;
import pl.agh.po.vpp.domain.Conditions;
import pl.agh.po.vpp.domain.PowerSource;

/**
 * Klasa bazowa dla źródeł wytwórczych (wzorzec Template Method).
 * Metoda szablonowa {@link #powerKw(Conditions)} uwzględnia awarię zasobu
 * oraz przycina moc surową do mocy znamionowej.
 */
public abstract class AbstractGenerator implements PowerSource {

    private final String id;
    private final String name;
    private final AssetType type;
    private final double ratedPowerKw;
    private AssetState state = AssetState.RUNNING;

    protected AbstractGenerator(String id, String name, AssetType type, double ratedPowerKw) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Identyfikator generatora nie może być pusty");
        }
        if (type == null) {
            throw new IllegalArgumentException("Typ generatora nie może być null");
        }
        if (ratedPowerKw < 0.0) {
            throw new IllegalArgumentException("Moc znamionowa nie może być ujemna: " + ratedPowerKw);
        }
        this.id = id;
        this.name = (name == null || name.isBlank()) ? id : name;
        this.type = type;
        this.ratedPowerKw = ratedPowerKw;
    }

    @Override
    public final double powerKw(Conditions conditions) {
        if (conditions == null) {
            throw new IllegalArgumentException("Conditions nie może być null");
        }
        if (state != AssetState.RUNNING || ratedPowerKw == 0.0) {
            return 0.0;
        }
        double raw = rawPowerKw(conditions);
        return Math.clamp(raw, 0.0, ratedPowerKw);
    }

    /** Alias zgodny z opisem wzorca Template Method w dokumentacji projektowej. */
    public final double produceKw(Conditions conditions) {
        return powerKw(conditions);
    }

    /**
     * Wylicza surową moc fizyczną [kW] w danych warunkach przed przycięciem do mocy znamionowej.
     */
    protected abstract double rawPowerKw(Conditions conditions);

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

    public double ratedPowerKw() {
        return ratedPowerKw;
    }

    public AssetState state() {
        return state;
    }

    public void setState(AssetState state) {
        if (state == null) {
            throw new IllegalArgumentException("Stan zasobu nie może być null");
        }
        this.state = state;
    }
}