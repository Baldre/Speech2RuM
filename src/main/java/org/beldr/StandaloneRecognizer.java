package org.beldr;

import static org.beldr.InfiniteSpeechRecognizer.langCode;

public class StandaloneRecognizer {

    //add 'GOOGLE_APPLICATION_CREDENTIALS=%credential path%' as environment variable before running
    public static void main(String... args) {
        try {
            InfiniteSpeechRecognizer.infiniteStreamingRecognize(langCode);
        } catch (Exception e) {
            System.out.println("Exception caught: " + e);
        }
    }
}
