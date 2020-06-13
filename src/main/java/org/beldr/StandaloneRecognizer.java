package org.beldr;

public class StandaloneRecognizer {

    //add 'GOOGLE_APPLICATION_CREDENTIALS=%credential path%' as environment variable before running
    public static void main(String... args) {
        try {
            InfiniteSpeechRecognizer.infiniteStreamingRecognize(true);
        } catch (Exception e) {
            System.out.println("Exception caught: " + e);
        }
    }
}
