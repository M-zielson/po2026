package agh.po.symulator;

import agh.po.symulator.Pozycja;
import agh.po.symulator.Silnik;
import agh.po.symulator.SkrzyniaBiegow;
import agh.po.symulator.Sprzeglo;

import java.util.Objects;

public class Samochod {
    private  Silnik silnik;
    private  SkrzyniaBiegow skrzynia;
    private  Sprzeglo sprzeglo;
    private  Pozycja pozycja;

    public Samochod()
    {
        this(new Silnik(), new SkrzyniaBiegow(), new Sprzeglo(), new Pozycja());
    }

    public Samochod(Silnik silnik, SkrzyniaBiegow skrzynia,
                    Sprzeglo sprzeglo, Pozycja pozycja)
    {
        this.silnik = Objects.requireNonNull(silnik, "silnik");
        this.skrzynia = Objects.requireNonNull(skrzynia, "skrzynia");
        this.sprzeglo = Objects.requireNonNull(sprzeglo, "sprzęgło");
        this.pozycja = Objects.requireNonNull(pozycja, "pozycja");
    }

    public void wlacz() {
        silnik.uruchom();
    }

    public void wylacz() {
        silnik.zatrzymaj();
        skrzynia.ustawLuz();
    }

    public Silnik getSilnik() {
        return silnik;
    }

    public SkrzyniaBiegow getSkrzynia() {
        return skrzynia;
    }

    public Sprzeglo getSprzeglo() {
        return sprzeglo;
    }

    public Pozycja getPozycja() {
        return pozycja;
    }

    @Override
    public String toString() {
        return "Samochód: silnik=" + (silnik.czyUruchomiony() ? "włączony" : "wyłączony")
                + ", obroty=" + silnik.getObroty()
                + ", bieg=" + skrzynia.getAktualnyBieg()
                + ", sprzęgło=" + (sprzeglo.getStanSprzegla() ? "wciśnięte" : "zwolnione")
                + ", pozycja=" + pozycja.getPozycja();
    }
}
