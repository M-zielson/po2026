package agh.po.symulator;

public class Sprzeglo extends Komponent {
    private boolean stanSprzegla;

    public Sprzeglo() {

        this("Nieznany", "Standardowe");
    }

    public Sprzeglo(String producent, String model) {

        super(producent, model);
    }

    public void wcisnij() {

        stanSprzegla = true;
    }

    public void zwolnij() {

        stanSprzegla = false;
    }

    public boolean getStanSprzegla() {

        return stanSprzegla;
    }
}
