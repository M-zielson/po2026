package pl.agh.po.vpp.app;

import org.junit.jupiter.api.Test;
import pl.agh.po.vpp.domain.Conditions;
import pl.agh.po.vpp.domain.OperatorSettings;
import pl.agh.po.vpp.domain.SimulationResult;
import pl.agh.po.vpp.domain.StepResult;
import pl.agh.po.vpp.domain.Timeline;
import pl.agh.po.vpp.domain.Weather;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsoleProgressListenerTest {

    private static final Timeline TIMELINE = Timeline.of(LocalDate.of(2026, 11, 13), ZoneId.of("Europe/Warsaw"));

    private static StepResult anyStep() {
        Instant time = TIMELINE.start();
        Conditions conditions = new Conditions(time, new Weather(time, 0, 0, 0, 0), 0.5);
        return new StepResult(time, TIMELINE.step(), conditions, List.of(), 0, 0, 0, 0, 0);
    }

    @Test
    void paskiPostepuINowaLiniaNaKoniec() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ConsoleProgressListener listener = new ConsoleProgressListener(new PrintStream(buffer, true, StandardCharsets.UTF_8), 4);

        for (int i = 0; i < 4; i++) {
            listener.onStep(anyStep());
        }
        listener.onFinished(new SimulationResult(TIMELINE, OperatorSettings.defaults(), List.of()));

        String output = buffer.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("1/4 kroków", "2/4 kroków", "4/4 kroków");
        assertThat(output).contains("[" + "#".repeat(30) + "]");
        assertThat(output).endsWith(System.lineSeparator());
    }

    @Test
    void liczbaKrokowMusiBycDodatnia() {
        assertThatThrownBy(() -> new ConsoleProgressListener(System.out, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
