package agh.po.lab3;

public class CodingBat {

    public static void main(String[] args)
    {
        CodingBat metody = new CodingBat();
        System.out.println(metody. sleepIn(true,false));
        System.out.println(metody.monkeyTrouble(true,true));
        System.out.println(metody.countEvens(new int[]{1, 2, 4, 5, 2}));
        System.out.println(metody.makeOutWord("<<>>", "Crazy"));

    }

    public boolean sleepIn(boolean weekday, boolean vacation) {
        if (weekday !=  true || vacation == true)
        {
            return true;
        }
        else
        {
            return false;
        }
    }

    public boolean monkeyTrouble(boolean aSmile, boolean bSmile) {
        if ((aSmile == true && bSmile == true) || (aSmile == false && bSmile == false))
        {
            return true;
        }
        else
        {
            return false;
        }
    }

    public int countEvens(int[] nums) {
        int counter = 0;
        for (int liczba : nums)
        {
            if (liczba % 2 == 0)
            {
                counter ++;
            }
        }
        return counter;
    }

    public String makeOutWord(String out, String word) {
        return(out.substring(0,2) + word + out.substring(2,4));
    }



}
