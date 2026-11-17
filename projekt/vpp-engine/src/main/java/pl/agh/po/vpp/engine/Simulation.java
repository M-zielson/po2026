package pl.agh.po.vpp.engine;

import pl.agh.po.vpp.domain.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Pętla doby zależna wyłącznie od interfejsów domain. Błąd przerywa przebieg bez onFinished. */
public final class Simulation {
    private final VirtualPowerPlant plant;
    private final Location location;
    private final OperatorSettings settings;
    private final WeatherProvider weatherProvider;
    private final PriceProvider priceProvider;
    private final List<SimulationListener> listeners;

    public Simulation(VirtualPowerPlant plant, Location location, OperatorSettings settings,
                      WeatherProvider weatherProvider, PriceProvider priceProvider, List<SimulationListener> listeners) {
        this.plant = Objects.requireNonNull(plant);
        this.location = Objects.requireNonNull(location);
        this.settings = Objects.requireNonNull(settings);
        this.weatherProvider = Objects.requireNonNull(weatherProvider);
        this.priceProvider = Objects.requireNonNull(priceProvider);
        this.listeners = List.copyOf(listeners);
        if (settings.strategy() != StrategyType.RULE_BASED || settings.batteryMode() != BatteryMode.AUTO
                || !settings.outages().isEmpty() || settings.hasExportLimit()) {
            throw new IllegalArgumentException("M1 nie obsługuje jeszcze strategii, sterowania baterią, awarii ani limitu eksportu");
        }
    }

    public SimulationResult run(Timeline timeline) {
        if (!timeline.zone().equals(location.zone()) || timeline.step().getNano() != 0) {
            throw new IllegalArgumentException("Timeline wymaga strefy osiedla i całkowitych sekund kroku");
        }
        var times = timeline.steps();
        var weather = weatherProvider.weatherFor(timeline, location);
        var prices = priceProvider.pricesFor(timeline);
        if (weather == null || prices == null || weather.size() != times.size() || prices.size() != times.size()) {
            throw new IllegalStateException("Provider zwrócił inną liczbę wpisów niż kroków osi czasu");
        }
        // Sprawdź całą dobę przed pierwszym powiadomieniem i obliczeniem.
        for (int i = 0; i < times.size(); i++) {
            Weather w = weather.get(i);
            Double price = prices.get(i);
            if (w == null || !times.get(i).equals(w.time()) || price == null || !Double.isFinite(price)
                    || !Double.isFinite(w.temperatureC()) || !Double.isFinite(w.tiltedIrradianceWm2())
                    || !Double.isFinite(w.windSpeed80mMs()) || !Double.isFinite(w.cloudCoverPct())) {
                throw new IllegalStateException("Niepoprawne dane providera w kroku " + i);
            }
        }
        List<StepResult> steps = new ArrayList<>(times.size());
        for (int i = 0; i < times.size(); i++) {
            var conditions = new Conditions(times.get(i), weather.get(i), prices.get(i));
            var step = plant.step(conditions, timeline.stepHours());
            steps.add(step);
        }
        var result = new SimulationResult(timeline, settings, steps);
        return result;
    }
}
