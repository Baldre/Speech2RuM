/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * Based on the speech recognition example provided by Google:
 * https://github.com/GoogleCloudPlatform/java-docs-samples/blob/master/speech/cloud-client/src/main/java/com/example/speech/InfiniteStreamRecognize.java
 */

package org.beldr;

import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;

import java.lang.Math;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.DataLine.Info;
import javax.sound.sampled.TargetDataLine;

public class InfiniteSpeechRecognizer {

    private static final int STREAMING_LIMIT = 290000; // ~5 minutes

    // Creating shared object
    private static final BlockingQueue<byte[]> sharedQueue = new LinkedBlockingQueue();
    private static TargetDataLine targetDataLine;
    private static final int BYTES_PER_BUFFER = 6400; // buffer size in bytes

    private static String lastRawString;
    private static StreamingRecognitionResult lastResult;

    private static StreamController referenceToStreamController;
    private static int restartCounter = 0;
    private static int finalRequestEndTime = 0;
    private static double bridgingOffset = 0;
    private static ArrayList<ByteString> audioInput = new ArrayList<>();
    private static ArrayList<ByteString> lastAudioInput = new ArrayList<>();
    private static boolean newStream = true;
    public static boolean keepOnlistening;
    static boolean lastTranscriptWasFinal = false;
    static int resultEndTimeInMS = 0;
    static int isFinalEndTime = 0;

    private final static RecognitionConfig recognitionConfig = RecognitionConfig.newBuilder()
            .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
            .setLanguageCode("en-US")
            .setSampleRateHertz(16000)
            .build();

    private final static StreamingRecognitionConfig streamingRecognitionConfig = StreamingRecognitionConfig.newBuilder()
            .setConfig(recognitionConfig)
            .setInterimResults(true)
            .build();

    private final static StreamingRecognizeRequest confRequest = StreamingRecognizeRequest.newBuilder()
            .setStreamingConfig(streamingRecognitionConfig)
            .build();

    private final static AudioFormat audioFormat = new AudioFormat(16000, 16, 1, true, false);

    /**
     * Performs infinite streaming speech recognition
     */
    public static void infiniteStreamingRecognize() throws Exception {
        infiniteStreamingRecognize(false);
    }


    public static void infiniteStreamingRecognize(boolean standAlone) throws Exception {

        try (SpeechClient client = SpeechClient.create()) {
            // Creating microphone input buffer thread
            MicBuffer micrunnable = new MicBuffer();
            Thread micThread = new Thread(micrunnable);
            ResponseObserver<StreamingRecognizeResponse> responseObserver = standAlone ? convertingSpeechRecognizer : rawStringSpeechRecognizer;

            ClientStream<StreamingRecognizeRequest> clientStream;
            clientStream = client.streamingRecognizeCallable().splitCall(responseObserver);
            StreamingRecognizeRequest request = confRequest; // The first request in a streaming call has to be a config
            clientStream.send(request);

            try {
                DataLine.Info targetInfo = new Info(TargetDataLine.class, audioFormat); // Set the system information to read from the microphone audio stream

                if (!AudioSystem.isLineSupported(targetInfo)) {
                    System.out.println("Microphone not supported");
                    System.exit(0);
                }
                // Target data line captures the audio stream the microphone produces.
                targetDataLine = (TargetDataLine) AudioSystem.getLine(targetInfo);
                targetDataLine.open(audioFormat);
                micThread.start();

                long startTime = System.currentTimeMillis();
                keepOnlistening = true;
                while (keepOnlistening) {

                    long estimatedTime = System.currentTimeMillis() - startTime;
                    if (estimatedTime >= STREAMING_LIMIT) {

                        clientStream.closeSend();
                        referenceToStreamController.cancel(); // remove Observer

                        if (resultEndTimeInMS > 0) {
                            finalRequestEndTime = isFinalEndTime;
                        }
                        resultEndTimeInMS = 0;

                        lastAudioInput = null;
                        lastAudioInput = audioInput;
                        audioInput = new ArrayList<>();

                        restartCounter++;

                        if (!lastTranscriptWasFinal) {
                            System.out.print('\n');
                        }

                        newStream = true;

                        clientStream = client.streamingRecognizeCallable().splitCall(responseObserver);

                        request = StreamingRecognizeRequest.newBuilder()
                                .setStreamingConfig(streamingRecognitionConfig)
                                .build();

                        System.out.printf("%d: RESTARTING REQUEST\n", restartCounter * STREAMING_LIMIT);
                        startTime = System.currentTimeMillis();

                    } else {

                        if ((newStream) && (lastAudioInput.size() > 0)) {
                            // if this is the first audio from a new request
                            // calculate amount of unfinalized audio from last request
                            // resend the audio to the speech client before incoming audio
                            double chunkTime = STREAMING_LIMIT / lastAudioInput.size();
                            // ms length of each chunk in previous request audio arrayList
                            if (chunkTime != 0) {
                                if (bridgingOffset < 0) {
                                    // bridging Offset accounts for time of resent audio calculated from last request
                                    bridgingOffset = 0;
                                }
                                if (bridgingOffset > finalRequestEndTime) {
                                    bridgingOffset = finalRequestEndTime;
                                }
                                int chunksFromMS = (int) Math.floor((finalRequestEndTime - bridgingOffset) / chunkTime);
                                // chunks from MS is number of chunks to resend
                                bridgingOffset = (int) Math.floor((lastAudioInput.size() - chunksFromMS) * chunkTime);
                                // set bridging offset for next request
                                for (int i = chunksFromMS; i < lastAudioInput.size(); i++) {
                                    request = StreamingRecognizeRequest.newBuilder()
                                            .setAudioContent(lastAudioInput.get(i))
                                            .build();
                                    clientStream.send(request);
                                }
                            }
                            newStream = false;
                        }

                        ByteString tempByteString = ByteString.copyFrom(sharedQueue.take());

                        request = StreamingRecognizeRequest.newBuilder()
                                .setAudioContent(tempByteString)
                                .build();

                        audioInput.add(tempByteString);
                    }

                    clientStream.send(request);
                }
            } catch (final Exception e) {
                System.out.println(e);
            } finally {
                micThread.interrupt();
                clientStream.closeSend();
                targetDataLine.close();
            }
        }
    }

    public static ResponseObserver<StreamingRecognizeResponse> rawStringSpeechRecognizer = new ResponseObserver<StreamingRecognizeResponse>() {

        public void onStart(StreamController controller) {
            referenceToStreamController = controller;
        }

        public void onResponse(StreamingRecognizeResponse response) {
            final StreamingRecognitionResult result = lastResult = response.getResultsList().get(0);
            if (result.getIsFinal()) {
                lastRawString = result.getAlternativesList().get(0).getTranscript().trim().toLowerCase();
                keepOnlistening = false;
            }
        }

        public void onComplete() {
        }

        public void onError(Throwable t) {
        }
    };

    private static final ResponseObserver<StreamingRecognizeResponse> convertingSpeechRecognizer = new ResponseObserver<StreamingRecognizeResponse>() {

        public void onStart(StreamController controller) {
            LogicHandler.handleBeginning();
            referenceToStreamController = controller;
        }

        public void onResponse(StreamingRecognizeResponse response) {
            LogicHandler.handleResult(response);
        }

        public void onComplete() {
        }

        public void onError(Throwable t) {
        }
    };

    public static String getLastRawString() {
        return lastRawString;
    }

    public static StreamingRecognitionResult getLastResult() {
        return lastResult;
    }

    private static class MicBuffer implements Runnable {

        @Override
        public void run() {
            targetDataLine.start();
            byte[] data = new byte[BYTES_PER_BUFFER];
            while (targetDataLine.isOpen()) {
                try {
                    int numBytesRead = targetDataLine.read(data, 0, data.length);
                    if ((numBytesRead <= 0) && (targetDataLine.isOpen())) {
                        continue;
                    }
                    sharedQueue.put(data.clone());
                } catch (InterruptedException e) {
                    System.out.println("Microphone input buffering interrupted : " + e.getMessage());
                }
            }
        }
    }
}
