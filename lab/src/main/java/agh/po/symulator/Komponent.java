package agh.po.symulator;

public class Komponent {
    private String producent;
    private String model;

    public Komponent(String producent, String model)
    {
        this.producent = producent;
        this.model = model;
    }

    public String getProducent()
    {
        return this.producent;
    }

    public String getModel()
    {
        return this.model;
    }


}
