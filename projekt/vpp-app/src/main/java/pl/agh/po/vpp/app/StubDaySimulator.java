package pl.agh.po.vpp.app;

import pl.agh.po.vpp.domain.AssetConfig;
import pl.agh.po.vpp.domain.AssetFlow;
import pl.agh.po.vpp.domain.AssetType;
import pl.agh.po.vpp.domain.Conditions;
import pl.agh.po.vpp.domain.EstateConfig;
import pl.agh.po.vpp.domain.OperatorSettings;
import pl.agh.po.vpp.domain.PriceProvider;
import pl.agh.po.vpp.domain.SimulationListener;
import pl.agh.po.vpp.domain.SimulationResult;
import pl.agh.po.vpp.domain.StepResult;
import pl.agh.po.vpp.domain.Timeline;
import pl.agh.po.vpp.domain.Weather;
import pl.agh.po.vpp.domain.WeatherProvider;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * TYMCZASOWA atrapa silnika, dopóki vpp-engine nie dostarczy prawdziwej symulacji.
 * Celowo bardzo prosta – służy tylko do sprawdzenia całego łańcucha trybu konsolowego:
 * <ul>
 *   <li>panele: moc liniowo z promieniowania, {@code pvPeakKw × G / 1000}, bez poprawki temperaturowej,</li>
 *   <li>zużycie: stałe {@value #LOAD_PER_DWELLING_KW} kW na mieszkanie; szkoła {@value #SCHOOL_LOAD_KW} kW
 *       w dni robocze 8–15, poza tym 0,</li>
 *   <li>wiatrak i magazyn nie pracują (moc 0); całą różnicę bilansuje sieć.</li>
 * </ul>
 * Bilans energii i konwencje znaków z kontraktu są zachowane w każdym kroku.
 */
public class StubDaySimulator implements DaySimulator {

    static final double LOAD_PER_DWELLING_KW = 0.5;
    static final double SCHOOL_LOAD_KW = 10;
    private static final int SCHOOL_OPENS_HOUR = 8;
    private static final int SCHOOL_CLOSES_HOUR = 15;

    @Override
    public SimulationResult simulate(EstateConfig estate, Timeline timeline, OperatorSettings settings,
                                     WeatherProvider weatherProvider, PriceProvider priceProvider,
                                     List<SimulationListener> listeners) {
        List<Instant> times = timeline.steps();
        List<Weather> weather = weatherProvider.weatherFor(timeline, estate.location());
        List<Double> prices = priceProvider.pricesFor(timeline);
        if (weather.size() != times.size() || prices.size() != times.size()) {
            throw new IllegalStateException("Provider zwrócił inną liczbę wpisów niż kroków osi czasu");
        }
        AssetConfig grid = estate.assetsOfType(AssetType.GRID).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Osiedle nie ma przyłącza do sieci (GRID)"));

        List<StepResult> steps = new ArrayList<>(times.size());
        for (int i = 0; i < times.size(); i++) {
            Instant time = times.get(i);
            Conditions conditions = new Conditions(time, weather.get(i), prices.get(i));
            StepResult step = step(estate, grid, timeline, conditions);
            steps.add(step);
            listeners.forEach(listener -> listener.onStep(step));
        }

        SimulationResult result = new SimulationResult(timeline, settings, steps);
        listeners.forEach(listener -> listener.onFinished(result));
        return result;
    }

    private StepResult step(EstateConfig estate, AssetConfig grid, Timeline timeline, Conditions conditions) {
        LocalDateTime localTime = LocalDateTime.ofInstant(conditions.time(), timeline.zone());
        double irradianceWm2 = conditions.weather().tiltedIrradianceWm2();

        List<AssetFlow> flows = new ArrayList<>();
        double productionKw = 0;
        double consumptionKw = 0;

        for (AssetConfig asset : estate.assets()) {
            switch (asset.type()) {
                case APARTMENT_BLOCK, HOUSE, SCHOOL -> {
                    double pvKw = asset.pvPeakKw() * irradianceWm2 / 1000;
                    double loadKw = loadKw(asset, localTime);
                    flows.add(new AssetFlow(asset.id(), asset.type(), pvKw, loadKw));
                    productionKw += pvKw;
                    consumptionKw += loadKw;
                }
                case WIND_TURBINE, BATTERY -> flows.add(new AssetFlow(asset.id(), asset.type(), 0, 0));
                case GRID -> {
                    // przyłącze dodajemy na końcu, gdy znamy bilans
                }
            }
        }

        // Bilans: production + grid = consumption + battery, a battery = 0.
        double gridKw = consumptionKw - productionKw;
        flows.add(new AssetFlow(grid.id(), AssetType.GRID, Math.max(gridKw, 0), Math.max(-gridKw, 0)));

        return new StepResult(conditions.time(), timeline.step(), conditions, flows,
                productionKw, consumptionKw, 0, gridKw, 0);
    }

    private static double loadKw(AssetConfig building, LocalDateTime localTime) {
        if (building.type() == AssetType.SCHOOL) {
            boolean weekday = localTime.getDayOfWeek() != DayOfWeek.SATURDAY
                    && localTime.getDayOfWeek() != DayOfWeek.SUNDAY;
            int hour = localTime.getHour();
            boolean lessons = hour >= SCHOOL_OPENS_HOUR && hour < SCHOOL_CLOSES_HOUR;
            return weekday && lessons ? SCHOOL_LOAD_KW : 0;
        }
        return building.dwellings() * LOAD_PER_DWELLING_KW;
    }
}
