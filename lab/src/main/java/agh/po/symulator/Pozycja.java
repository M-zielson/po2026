package agh.po.symulator;

public class Pozycja {
    private double x;
    private double y;

    public Pozycja()
    {
        this(0.0, 0.0);
    }

    public Pozycja(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public void aktualizujPozycję(double deltaX, double deltaY) {
        x += deltaX;
        y += deltaY;
    }

    public String getPozycja()
    {
        return "(" + x + ", " + y + ")";
    }

    public double getX()
    {
        return x;
    }

    public double getY()
    {
        return y;
    }
}