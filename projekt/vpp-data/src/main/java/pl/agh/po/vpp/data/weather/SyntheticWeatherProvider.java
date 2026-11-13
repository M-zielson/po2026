package pl.agh.po.vpp.data.weather;

import pl.agh.po.vpp.domain.Location;
import pl.agh.po.vpp.domain.Timeline;
import pl.agh.po.vpp.domain.Weather;
import pl.agh.po.vpp.domain.WeatherProvider;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Sztuczna, ale realistyczna pogoda: działa bez internetu i jest powtarzalna.
 * Ten sam seed i ten sam dzień dają zawsze identyczny wynik; różne dni dają różną pogodę.
 *
 * <ul>
 *   <li>słońce – z pozycji słońca dla miejsca, dnia i godziny (model nieba bezchmurnego),
 *       osłabiane przez chmury; panele skierowane na południe pod kątem {@value #PANEL_TILT_DEG}°,</li>
 *   <li>chmury i wiatr – wolno zmieniające się wartości losowe wokół średniej wylosowanej na dany dzień,</li>
 *   <li>temperatura – średnia sezonowa dla Krakowa, najchłodniej ok. 3:00, najcieplej ok. 15:00.</li>
 * </ul>
 *
 * Model zakłada półkulę północną (panele zwrócone na południe).
 */
public class SyntheticWeatherProvider implements WeatherProvider {

    /** Nachylenie paneli [°]; ta sama wartość posłuży przy zapytaniach do Open-Meteo. */
    public static final double PANEL_TILT_DEG = 35;

    /** Moc promieniowania słonecznego nad atmosferą [W/m²]. */
    private static final double SOLAR_CONSTANT_WM2 = 1353;
    /** Część promieniowania bezpośredniego docierająca jako rozproszone (przybliżenie dla czystego nieba). */
    private static final double DIFFUSE_SHARE = 0.1;
    /** Poniżej tej wysokości słońca (sinus kąta) traktujemy je jak schowane za horyzontem. */
    private static final double MIN_SUN_ELEVATION_SIN = 0.01;
    private static final double MAX_WIND_MS = 30;

    /** Jak szybko chmury i wiatr wracają do średniej dnia (0 = wcale, 1 = od razu). */
    private static final double MEAN_REVERSION = 0.1;
    private static final double CLOUD_NOISE_PCT = 5;
    private static final double WIND_NOISE_MS = 0.8;

    private final long seed;

    public SyntheticWeatherProvider(long seed) {
        this.seed = seed;
    }

    @Override
    public List<Weather> weatherFor(Timeline timeline, Location location) {
        Random random = new Random(daySeed(timeline.date()));
        DayCharacter day = DayCharacter.draw(random, timeline.date());

        double cloudPct = day.baseCloudPct();
        double windMs = day.meanWindMs();
        List<Weather> result = new ArrayList<>(timeline.size());

        for (Instant time : timeline.steps()) {
            cloudPct = clamp(cloudPct + MEAN_REVERSION * (day.baseCloudPct() - cloudPct)
                    + random.nextGaussian() * CLOUD_NOISE_PCT, 0, 100);
            windMs = clamp(windMs + MEAN_REVERSION * (day.meanWindMs() - windMs)
                    + random.nextGaussian() * WIND_NOISE_MS, 0, MAX_WIND_MS);

            // Słońce liczymy w połowie kroku, żeby kroki o świcie i zmierzchu nie były przesunięte.
            Instant midStep = time.plus(timeline.step().dividedBy(2));
            double irradianceWm2 = clearSkyTiltedIrradianceWm2(midStep, location) * cloudFactor(cloudPct);
            double temperatureC = day.temperatureAt(time.atZone(timeline.zone()).toLocalTime());

            result.add(new Weather(time, irradianceWm2, windMs, temperatureC, cloudPct));
        }
        return List.copyOf(result);
    }

    /** Ziarno dla konkretnego dnia: ten sam seed daje różną pogodę w różne dni, ale zawsze tę samą dla danego dnia. */
    private long daySeed(LocalDate date) {
        return seed * 1_000_003L + date.toEpochDay();
    }

    /**
     * Promieniowanie na nachylony panel przy bezchmurnym niebie [W/m²].
     * Pozycja słońca z deklinacji i kąta godzinnego; tłumienie atmosfery z modelu Meinela.
     */
    static double clearSkyTiltedIrradianceWm2(Instant time, Location location) {
        double latitude = Math.toRadians(location.latitude());
        double tilt = Math.toRadians(PANEL_TILT_DEG);

        int dayOfYear = time.atZone(ZoneOffset.UTC).getDayOfYear();
        double declination = Math.toRadians(23.44) * Math.sin(2 * Math.PI * (284 + dayOfYear) / 365.0);

        double utcHours = time.atZone(ZoneOffset.UTC).toLocalTime().toSecondOfDay() / 3600.0;
        double solarHours = utcHours + location.longitude() / 15.0;
        double hourAngle = Math.toRadians(15 * (solarHours - 12));

        double sinElevation = Math.sin(latitude) * Math.sin(declination)
                + Math.cos(latitude) * Math.cos(declination) * Math.cos(hourAngle);
        if (sinElevation < MIN_SUN_ELEVATION_SIN) {
            return 0;
        }

        double airMass = 1 / sinElevation;
        double directNormalWm2 = SOLAR_CONSTANT_WM2 * Math.pow(0.7, Math.pow(airMass, 0.678));

        // Kąt padania na panel zwrócony na południe, nachylony o PANEL_TILT_DEG.
        double cosIncidence = Math.sin(declination) * Math.sin(latitude - tilt)
                + Math.cos(declination) * Math.cos(latitude - tilt) * Math.cos(hourAngle);
        double beamWm2 = directNormalWm2 * Math.max(0, cosIncidence);
        double diffuseWm2 = DIFFUSE_SHARE * directNormalWm2 * (1 + Math.cos(tilt)) / 2;

        return beamWm2 + diffuseWm2;
    }

    /** Jaka część słońca przechodzi przez chmury (wzór Kastena–Czeplaka): 1 przy 0%, 0,25 przy 100%. */
    static double cloudFactor(double cloudPct) {
        return 1 - 0.75 * Math.pow(cloudPct / 100, 3.4);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Cechy całego dnia losowane raz: jak bardzo pochmurno, jak wietrznie, jak ciepło. */
    private record DayCharacter(double baseCloudPct, double meanWindMs, double meanTemperatureC,
                                double dailyAmplitudeC) {

        static DayCharacter draw(Random random, LocalDate date) {
            double baseCloudPct = random.nextDouble() * 90;
            double meanWindMs = 2 + random.nextDouble() * 8;
            double meanTemperatureC = seasonalMeanTemperatureC(date) + random.nextGaussian() * 3;
            // W pochmurny dzień różnica między dniem a nocą jest mniejsza.
            double dailyAmplitudeC = 3 + 4 * (1 - baseCloudPct / 100);
            return new DayCharacter(baseCloudPct, meanWindMs, meanTemperatureC, dailyAmplitudeC);
        }

        /** Średnia temperatura dobowa w Krakowie: ok. −2,5 °C w połowie stycznia, ok. 19,5 °C w połowie lipca. */
        static double seasonalMeanTemperatureC(LocalDate date) {
            return 8.5 - 11 * Math.cos(2 * Math.PI * (date.getDayOfYear() - 15) / 365.25);
        }

        /** Najcieplej ok. 15:00, najchłodniej ok. 3:00. */
        double temperatureAt(LocalTime localTime) {
            double hour = localTime.toSecondOfDay() / 3600.0;
            return meanTemperatureC + dailyAmplitudeC * Math.cos(2 * Math.PI * (hour - 15) / 24);
        }
    }
}
