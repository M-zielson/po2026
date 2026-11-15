package pl.agh.po.vpp.engine.asset;

import pl.agh.po.vpp.domain.AssetType;
import pl.agh.po.vpp.domain.Conditions;

/**
 * Instalacja fotowoltaiczna z poprawką temperaturową:
 * P = A * eta * (G / 1000) * (1 + gamma * (T - 25)), przycięte do mocy znamionowej.
 */
public class SolarPanel extends AbstractGenerator {

    public static final double DEFAULT_EFFICIENCY = 0.20;
    public static final double DEFAULT_TEMP_COEFFICIENT_PER_C = -0.004;
    private static final double STC_TEMPERATURE_C = 25.0;
    private static final double W_PER_KW = 1000.0;

    private final double areaM2;
    private final double efficiency;
    private final double tempCoefficientPerC;

    public SolarPanel(String id, String name, AssetType hostType, double ratedPowerKw,
                      double areaM2, double efficiency, double tempCoefficientPerC) {
        super(id, name, hostType, ratedPowerKw);
        if (areaM2 < 0.0) {
            throw new IllegalArgumentException("Powierzchnia paneli nie może być ujemna: " + areaM2);
        }
        if (efficiency <= 0.0 || efficiency > 1.0) {
            throw new IllegalArgumentException("Sprawność paneli poza zakresem (0, 1]: " + efficiency);
        }
        this.areaM2 = areaM2;
        this.efficiency = efficiency;
        this.tempCoefficientPerC = tempCoefficientPerC;
    }

    /**
     * Konstruktor dla instalacji dachowej budynku na podstawie mocy szczytowej pvPeakKw z estate.json
     * (w warunkach STC 1000 W/m2 i 25°C iloczyn A * eta jest równy pvPeakKw).
     */
    public SolarPanel(String id, String name, AssetType hostType, double pvPeakKw) {
        this(
                id,
                name,
                hostType,
                pvPeakKw,
                pvPeakKw / DEFAULT_EFFICIENCY,
                DEFAULT_EFFICIENCY,
                DEFAULT_TEMP_COEFFICIENT_PER_C
        );
    }

    public SolarPanel(String id, String name, double pvPeakKw) {
        this(id, name, AssetType.APARTMENT_BLOCK, pvPeakKw);
    }

    @Override
    protected double rawPowerKw(Conditions conditions) {
        double irradianceKwPerM2 = conditions.weather().tiltedIrradianceWm2() / W_PER_KW;
        if (irradianceKwPerM2 <= 0.0 || areaM2 == 0.0) {
            return 0.0;
        }
        double deltaTemp = conditions.weather().temperatureC() - STC_TEMPERATURE_C;
        double tempFactor = Math.max(0.0, 1.0 + tempCoefficientPerC * deltaTemp);
        return areaM2 * efficiency * irradianceKwPerM2 * tempFactor;
    }

    public double areaM2() {
        return areaM2;
    }

    public double efficiency() {
        return efficiency;
    }

    public double tempCoefficientPerC() {
        return tempCoefficientPerC;
    }
}