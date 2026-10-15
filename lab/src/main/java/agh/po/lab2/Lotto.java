package agh.po.lab2;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.Random;
import java.util.Arrays;

public class Lotto
{
    public static void main(String[] args)
    {

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
        ArrayList<Integer> pewniaki = new ArrayList<>();
        System.out.print("Twoje typy: " + Arrays.toString(args) + "\n");
        System.out.print("Wylosowane liczby: " + wylosowane + "\n");
        //System.out.print(wylosowane.retainAll(pewniaki).count());

    }
}
