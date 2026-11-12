package agh.po.Lab4.animals;

public class Dog extends Animal {

    public Dog(String name) {
        super(name, 4);
    }

    @Override
    public String getDescription() {
        return "Dog [name=" + name + ", legs=" + legs + "]";
    }

    @Override
    public void makeSound() {
        System.out.println(name + " (Pies) szczeka: Hau hau!");
    }
}