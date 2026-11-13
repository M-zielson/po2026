package pl.agh.po.vpp.data.price;

import pl.agh.po.vpp.domain.PriceProvider;
import pl.agh.po.vpp.domain.Timeline;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Sztuczne ceny energii z sieci [PLN/kWh] o typowym kształcie dobowym:
 * tanio w nocy, szczyt rano, dołek w południe (dużo energii z paneli w całym kraju),
 * najdrożej wieczorem. W weekend taniej. Działa bez internetu i jest powtarzalne:
 * ten sam seed i ten sam dzień dają zawsze te same ceny.
 *
 * <p>Uproszczenia: jedna cena dla zakupu i sprzedaży, brak sezonowości i cen ujemnych.
 * Prawdziwe ceny z TGE dostarczy w M3 {@code CsvPriceProvider}.
 */
public class SyntheticPriceProvider implements PriceProvider {

    /** Cena bazowa w dzień roboczy dla każdej godziny lokalnej 0–23 [PLN/kWh]. */
    private static final double[] WEEKDAY_PROFILE_PLN_PER_KWH = {
            0.45, 0.45, 0.45, 0.45, 0.45, 0.45,   // 0–5   noc
            0.60,                                  // 6     narastanie
            0.85, 0.85, 0.85,                      // 7–9   szczyt poranny
            0.60,                                  // 10
            0.50, 0.50, 0.50, 0.50,                // 11–14 dołek solarny
            0.60,                                  // 15
            0.75,                                  // 16
            1.10, 1.10, 1.10, 1.10,                // 17–20 szczyt wieczorny
            0.80, 0.65, 0.55                       // 21–23 spadek
    };

    /** W weekend przemysł zużywa mniej, więc energia jest tańsza. */
    private static final double WEEKEND_FACTOR = 0.85;
    /** Odchylenie poziomu całego dnia (jeden dzień droższy, inny tańszy) i jego granica. */
    private static final double DAY_LEVEL_SIGMA = 0.05;
    private static final double DAY_LEVEL_LIMIT = 0.10;
    /** Drobny szum w każdym kroku, żeby wykres nie był schodkami, i jego granica. */
    private static final double STEP_NOISE_SIGMA = 0.015;
    private static final double STEP_NOISE_LIMIT = 0.03;

    /**
     * Stała mieszana z ziarnem. Bez niej ceny i pogoda z tym samym seedem dostałyby
     * identyczny ciąg liczb losowych i szum cen pokrywałby się z szumem chmur.
     */
    private static final long SALT = 0x9E3779B97F4A7C15L;

    private final long seed;

    public SyntheticPriceProvider(long seed) {
        this.seed = seed;
    }

    @Override
    public List<Double> pricesFor(Timeline timeline) {
        Random random = new Random(daySeed(timeline.date()));
        double dayLevel = 1 + limited(random.nextGaussian() * DAY_LEVEL_SIGMA, DAY_LEVEL_LIMIT);
        double weekFactor = isWeekend(timeline.date().getDayOfWeek()) ? WEEKEND_FACTOR : 1;

        List<Double> prices = new ArrayList<>(timeline.size());
        for (Instant time : timeline.steps()) {
            int localHour = time.atZone(timeline.zone()).getHour();
            double noise = 1 + limited(random.nextGaussian() * STEP_NOISE_SIGMA, STEP_NOISE_LIMIT);
            prices.add(WEEKDAY_PROFILE_PLN_PER_KWH[localHour] * weekFactor * dayLevel * noise);
        }
        return List.copyOf(prices);
    }

    /** Ziarno dla konkretnego dnia: różne dni mają różne ceny, ale dany dzień zawsze te same. */
    private long daySeed(LocalDate date) {
        return (seed * 1_000_003L + date.toEpochDay()) ^ SALT;
    }

    private static boolean isWeekend(DayOfWeek day) {
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    /** Przycina wartość do przedziału [−limit, limit]. */
    private static double limited(double value, double limit) {
        return Math.max(-limit, Math.min(limit, value));
    }
}
