package pl.agh.po.vpp.app;

import pl.agh.po.vpp.domain.*;
import pl.agh.po.vpp.engine.EstateBuilder;
import pl.agh.po.vpp.engine.Simulation;
import java.util.List;

/** Adapter rzeczywistego silnika M1 do istniejącej aplikacji. */
public final class EngineDaySimulator implements DaySimulator {
    @Override
    public SimulationResult simulate(EstateConfig estate, Timeline timeline, OperatorSettings settings,
                                     WeatherProvider weather, PriceProvider prices, List<SimulationListener> listeners) {
        return new Simulation(new EstateBuilder().build(estate), estate.location(), settings,
                weather, prices, listeners).run(timeline);
    }
}
