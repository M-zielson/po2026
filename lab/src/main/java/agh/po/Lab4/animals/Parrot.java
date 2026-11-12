package agh.po.Lab4.animals;

public class Parrot extends Animal {

    public Parrot(String name) {
        super(name, 2);
    }

    @Override
    public String getDescription() {
        return "Parrot [name=" + name + ", legs=" + legs + "]";
    }

    @Override
    public void makeSound() {
        System.out.println(name + " (Papuga) mówi: Kraa! Cześć!");
    }
}