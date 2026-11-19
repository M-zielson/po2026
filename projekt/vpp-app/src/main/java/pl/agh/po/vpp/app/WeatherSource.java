package pl.agh.po.vpp.app;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** Skąd tryb konsolowy bierze pogodę (opcja {@code --weather}). */
public enum WeatherSource {
    /** Prawdziwa pogoda z Open-Meteo, z cache w plikach (działa bez internetu dla zapisanych dni). */
    OPENMETEO,
    /** Pogoda syntetyczna z seeda: zawsze dostępna i powtarzalna. */
    SYNTHETIC;

    /** Nazwa w argumentach programu, np. {@code openmeteo}. */
    public String optionValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** @throws IllegalArgumentException dla nieznanej nazwy, z listą dozwolonych */
    public static WeatherSource fromOption(String value) {
        for (WeatherSource source : values()) {
            if (source.optionValue().equals(value)) {
                return source;
            }
        }
        String allowed = Arrays.stream(values()).map(WeatherSource::optionValue).collect(Collectors.joining(", "));
        throw new IllegalArgumentException("Nieznane źródło pogody: " + value + " (dozwolone: " + allowed + ")");
    }
}
