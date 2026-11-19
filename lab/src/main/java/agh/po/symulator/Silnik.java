package agh.po.symulator;

import agh.po.symulator.Komponent;

public class Silnik extends Komponent {
    private int maxObroty;
    private int obroty;

    public Silnik() {
        this("Nieznany", "Standardowy", 6000);
    }

    public Silnik(String producent, String model, int maxObroty) {
        super(producent, model);
        if (maxObroty <= 0) {
            throw new IllegalArgumentException("Maksymalne obroty muszą być dodatnie.");
        }
        this.maxObroty = maxObroty;
    }

    public void uruchom() {

        obroty = Math.min(800, maxObroty);
    }

    public void zatrzymaj() {

        obroty = 0;
    }

    public int getMaxObroty() {

        return maxObroty;
    }

    public int getObroty() {

        return obroty;
    }

    public boolean czyUruchomiony() {
        return obroty > 0;
    }
}
