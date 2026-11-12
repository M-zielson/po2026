package agh.po.Lab4.zadania;

import agh.po.Lab4.animals.*;
import java.util.Random;

public class Zoo {
    private Animal[] animals = new Animal[100];

    public void fillZoo() {
        Random random = new Random();
        for (int i = 0; i < animals.length; i++) {
            int type = random.nextInt(3);
            switch (type) {
                case 0:
                    animals[i] = new Parrot("Papuga_" + i);
                    break;
                case 1:
                    animals[i] = new Dog("Pies_" + i);
                    break;
                case 2:
                    animals[i] = new Snake("Wąż_" + i);
                    break;
            }
        }
    }

    public int countAllLegs() {
        int sum = 0;
        for (Animal animal : animals) {
            if (animal != null) {
                int legs = animal.getLegs();
                sum += legs;
            }
        }
        return sum;
    }

    public void showAnimalsAndSounds() {
        for (Animal animal : animals) {
            if (animal != null) {
                String desc = animal.getDescription();
                System.out.println(desc);
                animal.makeSound();
            }
        }
    }

    public static void main(String[] args) {
        Zoo zoo = new Zoo();
        zoo.fillZoo();

        zoo.showAnimalsAndSounds();

        int totalLegs = zoo.countAllLegs();
        System.out.println("Suma nóg wszystkich zwierząt w Zoo: " + totalLegs);
    }
}