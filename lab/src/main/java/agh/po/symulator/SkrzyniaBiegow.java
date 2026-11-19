package agh.po.symulator;

public class SkrzyniaBiegow extends Komponent {
    private int aktualnyBieg;
    private final int iloscBiegow;

    public SkrzyniaBiegow() {
        this("Nieznany", "Manualna", 5);
    }

    public SkrzyniaBiegow(String producent, String model, int iloscBiegow) {
        super(producent, model);
        if (iloscBiegow <= 0) {
            throw new IllegalArgumentException("Liczba biegów musi być dodatnia.");
        }
        this.iloscBiegow = iloscBiegow;
    }

    public void zwiekszBieg() {
        if (aktualnyBieg < iloscBiegow) {
            aktualnyBieg++;
        }
    }

    public void zmniejszBieg() {
        if (aktualnyBieg > 0) {
            aktualnyBieg--;
        }
    }

    public void ustawLuz() {
        aktualnyBieg = 0;
    }

    public int getAktualnyBieg() {
        return aktualnyBieg;
    }

    public int getIloscBiegow() {
        return iloscBiegow;
    }
}
