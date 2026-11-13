package pl.agh.po.vpp.app;

import pl.agh.po.vpp.domain.EstateConfig;
import pl.agh.po.vpp.domain.OperatorSettings;
import pl.agh.po.vpp.domain.PriceProvider;
import pl.agh.po.vpp.domain.SimulationListener;
import pl.agh.po.vpp.domain.SimulationResult;
import pl.agh.po.vpp.domain.Timeline;
import pl.agh.po.vpp.domain.WeatherProvider;

import java.util.List;

/**
 * Policzenie jednej doby osiedla – punkt, w którym aplikacja podłącza silnik.
 * Teraz: {@link StubDaySimulator} (atrapa). Docelowo: adapter na {@code Simulation} z vpp-engine,
 * podmieniany w jednym miejscu w {@link Main}, bez zmian w reszcie aplikacji.
 */
public interface DaySimulator {

    /**
     * @param listeners słuchacze powiadamiani o każdym kroku i o końcu symulacji
     * @return wyniki wszystkich kroków doby
     */
    SimulationResult simulate(EstateConfig estate, Timeline timeline, OperatorSettings settings,
                              WeatherProvider weather, PriceProvider prices,
                              List<SimulationListener> listeners);
}
