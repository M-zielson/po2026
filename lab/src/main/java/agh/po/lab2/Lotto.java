package agh.po.lab2;
import java.util.ArrayList;
import java.util.Random;

public class Lotto
{
    public static void main(String[] args)
    {
        ArrayList<Integer> pewniaki = new ArrayList<>();
        for (int i = 0;i<6;i++)
        {
            pewniaki.add(Integer.parseInt(args[i]));
        }

        ArrayList<Integer> wylosowane = new ArrayList<>();
        Random r = new Random();

        while (wylosowane.size() < 6)
        {
            int los = r.nextInt(49) + 1;
            if (wylosowane.contains(los))
            {
                continue;
            }
            else
            {
                wylosowane.add(los);
            }

        }

        System.out.print("Twoje typy: " + pewniaki + "\n");
        System.out.print("Wylosowane liczby: " + wylosowane + "\n");

        wylosowane.retainAll(pewniaki);
        int trafienia = wylosowane.toArray().length;

        System.out.print("Liczba trafień: " + trafienia);

    }
}
