package agh.po.Lab4.animals;

public class Snake extends Animal {

    public Snake(String name) {
        super(name, 0);
    }

    @Override
    public String getDescription() {
        return "Snake [name=" + name + ", legs=" + legs + "]";
    }

    @Override
    public void makeSound() {
        System.out.println(name + " (Wąż) syczy: Ssssss!");
    }
}
