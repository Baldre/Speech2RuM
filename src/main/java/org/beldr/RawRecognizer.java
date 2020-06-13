package org.beldr;

import java.util.Scanner;

public class RawRecognizer {

    //add 'GOOGLE_APPLICATION_CREDENTIALS=%credential path%' as environment variable before running
    public static void main(String[] args) {
        try {
            while (true) {
                Scanner scanner = new Scanner(System.in);
                if (System.in.available() > 0) {
                    String result = scanner.nextLine();
                    System.out.println("-" + result);
                    if (!result.equals("close")) {
                        InfiniteSpeechRecognizer.infiniteStreamingRecognize(false);
                        System.out.println(InfiniteSpeechRecognizer.getLastRawString());
                    } else {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Caught exception");
        }
    }
}
