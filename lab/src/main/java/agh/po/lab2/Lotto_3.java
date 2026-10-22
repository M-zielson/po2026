package agh.po.lab2;
import java.util.ArrayList;
import java.util.Random;

public class Lotto_3
{
    public static void main(String[] args)
    {
        ArrayList<Integer> pewniaki = new ArrayList<>();
        ArrayList<Integer> wylosowane = new ArrayList<>();
        Random r = new Random();

        int trafienia = 0;
        int licznik = 1;
        long start_czas = System.currentTimeMillis();

        for (int i = 0; i < 6; i++) {
            pewniaki.add(Integer.parseInt(args[i]));
        }
        while (trafienia < 6) {
            wylosowane.clear();
            while (wylosowane.size() < 6) {
                int los = r.nextInt(49) + 1;
                if (wylosowane.contains(los)) {
                    continue;
                } else {
                    wylosowane.add(los);
                }

            }
            trafienia = 0;
            for (int typ : pewniaki) {
                if (wylosowane.contains(typ)) {
                    trafienia++;
                }
            }
            licznik++;
        }

        long end_czas = System.currentTimeMillis();
        long czas = end_czas - start_czas;
        System.out.print("Liczba powtórzeń: " + licznik + "\n");
        System.out.print("Czas wykonania: " + czas/1000.0 + " sekund");
    }
}
