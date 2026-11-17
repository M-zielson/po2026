package pl.agh.po.vpp.engine.asset;

import pl.agh.po.vpp.domain.AssetType;
import pl.agh.po.vpp.domain.Conditions;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Pobór całej szkoły: baza także w weekend, zajęcia w dni robocze [8, 15). */
public final class School extends AbstractLoad {
    private final double baseKw;
    private final double lessonsKw;

    public School(String id, String name, ZoneId zone, SolarPanel roofPv) {
        this(id, name, zone, roofPv, 1, 10);
    }

    public School(String id, String name, ZoneId zone, SolarPanel roofPv, double baseKw, double lessonsKw) {
        super(id, name, AssetType.SCHOOL, zone, roofPv);
        if (!Double.isFinite(baseKw) || !Double.isFinite(lessonsKw) || baseKw < 0 || lessonsKw < baseKw) {
            throw new IllegalArgumentException("Niepoprawne moce szkoły");
        }
        this.baseKw = baseKw;
        this.lessonsKw = lessonsKw;
    }

    @Override
    protected double profileDemandKw(LocalDateTime time, Conditions conditions) {
        boolean lessons = time.getDayOfWeek().getValue() <= 5 && time.getHour() >= 8 && time.getHour() < 15;
        return lessons ? lessonsKw : baseKw;
    }
}
