package pl.agh.po.vpp.engine.asset;

import java.time.LocalDateTime;

/** Parametry na lokal. Wartości domyślne są założeniami modelu, nie pomiarami osiedla. */
public record ResidentialProfile(double baseKw, double morningKw, double eveningKw,
                                 double heatLossKwPerC, double balanceTemperatureC, double heatingCop) {
    public static final ResidentialProfile APARTMENT = new ResidentialProfile(0.15, 0.35, 0.65, 0.025, 16, 3);
    public static final ResidentialProfile HOUSE = new ResidentialProfile(0.20, 0.45, 0.85, 0.08, 16, 3);

    public ResidentialProfile {
        if (!Double.isFinite(baseKw) || !Double.isFinite(morningKw) || !Double.isFinite(eveningKw)
                || !Double.isFinite(heatLossKwPerC) || !Double.isFinite(balanceTemperatureC)
                || !Double.isFinite(heatingCop) || baseKw < 0 || morningKw < 0 || eveningKw < 0
                || heatLossKwPerC < 0 || heatingCop <= 0) {
            throw new IllegalArgumentException("Niepoprawne parametry profilu mieszkalnego");
        }
    }

    public double demandKw(LocalDateTime time, double temperatureC) {
        if (!Double.isFinite(temperatureC)) {
            throw new IllegalArgumentException("Temperatura musi być skończona");
        }
        double hour = time.toLocalTime().toSecondOfDay() / 3600.0;
        boolean weekend = time.getDayOfWeek().getValue() >= 6;
        // Gładkie szczyty aktywności; w weekend poranny szczyt przesuwa się o godzinę.
        double appliances = baseKw + morningKw * peak(hour, weekend ? 8.5 : 7.5, 1.0)
                + eveningKw * peak(hour, 19.5, 2.0);
        // P_el = H * max(T_balance - T_out, 0) / COP.
        double heating = heatLossKwPerC * Math.max(0, balanceTemperatureC - temperatureC) / heatingCop;
        return appliances + heating;
    }

    private static double peak(double hour, double center, double width) {
        double distance = Math.abs(hour - center);
        distance = Math.min(distance, 24 - distance);
        return Math.exp(-0.5 * Math.pow(distance / width, 2));
    }
}
