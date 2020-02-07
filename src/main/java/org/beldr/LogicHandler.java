package org.beldr;


import com.google.cloud.speech.v1.SpeechRecognitionAlternative;
import com.google.cloud.speech.v1.StreamingRecognitionResult;
import com.google.cloud.speech.v1.StreamingRecognizeResponse;
import com.google.protobuf.Duration;
import declareextraction.constructs.DeclareConstraint;
import declareextraction.constructs.condition.ActivationCondition;
import declareextraction.constructs.condition.Condition;
import declareextraction.constructs.condition.CorrelationCondition;
import declareextraction.constructs.condition.TimeCondition;
import declareextraction.textprocessing.ConditionParser;


import java.util.ArrayList;
import java.util.List;

import static org.beldr.InfiniteSpeechRecognizer.resultEndTimeInMS;
import static org.beldr.InfiniteSpeechRecognizer.isFinalEndTime;
import static org.beldr.InfiniteSpeechRecognizer.lastTranscriptWasFinal;
import static org.beldr.InfiniteSpeechRecognizer.keepOnlistening;


public class LogicHandler {
    private static List<DeclareConstraint> recordedConstraints = new ArrayList<>();

    private static final String WHITE = "\033[0;30m";
    private static final String RED = "\033[0;31m";
    private static final String GREEN = "\033[0;32m";

    private static final String greeting = "You can start speaking now!";
    private static final String availableCommands = "Available commands: \"record/create\", \"close\"";
    private static final String constraintCommands = "Modify previously created constraint: \"delete\", \"activation condition\", \"correlation condition\", \"time condition\"";

    //TODO: have examples of 'real' cases or parameter types?
    //$field ( (higher|greater|more|smaller|lower|less) than [or equal to] | ( is [not] | [not] in) ) $value+
    private static final String actCondExample = "Condition format: 'price greater/lower than (or equal to) 10.4', 'price is (not) 4', 'type (not) in value1, value2, value3'";
    //(same | different) $field
    private static final String corrCondExample = "Condition format: 'same price', 'different price'";
    //between $int and $int $timeunit | at most $int
    private static final String timeCondExample = "Condition format: 'between 4 and 6 seconds/minutes/hours/days', 'at most 7 seconds/minutes/hours/days'";

    private static boolean recordingRule = false;
    private static Condition.ConditionType recordingCondition = null;

    public static void handleBeginning() {
        System.out.println(greeting);
        System.out.println(getCommands());
    }

    public static void handleResult(StreamingRecognizeResponse response) {
        StreamingRecognitionResult result = response.getResultsList().get(0);
        Duration resultEndTime = result.getResultEndTime();
        resultEndTimeInMS = (int) ((resultEndTime.getSeconds() * 1000) + (resultEndTime.getNanos() / 1000000));

        SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);
        if (result.getIsFinal()) {
            System.out.print(GREEN);
            System.out.print("\033[2K\r"); //deletes previous line
            System.out.printf("%s [%.2f]\n",
                    alternative.getTranscript().trim(),
                    alternative.getConfidence()
            );
            isFinalEndTime = resultEndTimeInMS;
            lastTranscriptWasFinal = true;
            handleFinal(alternative.getTranscript().trim().toLowerCase());
        } else {
            System.out.print(RED);
            System.out.print("\033[2K\r");
            System.out.printf("%s", alternative.getTranscript().trim());
            lastTranscriptWasFinal = false;
        }
    }

    private static void handleFinal(String transcript) {
        System.out.print(WHITE);

        if (recordingRule) {
            try {
                List<DeclareConstraint> constraints = new ArrayList<>(DeclareUtil.generateModel(transcript).getConstraints());
                System.out.println("Added constraints:");
                for (DeclareConstraint constraint : constraints) {
                    if (constraint == null)
                        continue;
                    recordedConstraints.add(constraint);
                    System.out.println(constraint.toRuMString());
                }
            } catch (RuntimeException e) {
                System.err.println(e);
            } finally {
                recordingRule = false;
            }
            System.out.println(getCommands());
            return;
        } else if (recordingCondition != null) {
            switch (recordingCondition) {
                case ACTIVATION:
                    ActivationCondition actCond = ConditionParser.parseActivationCondition(transcript);
                    if (actCond != null) {
                        lastConstraint().setActivationCondition(actCond);
                        System.out.println("Parsed condition: " + actCond.toRuMString());
                    } else {
                        System.out.println("Unable to parse activation condition");
                    }
                    break;
                case CORRELATION:
                    CorrelationCondition corrCond = ConditionParser.parseCorrelationCondition(transcript);
                    if (corrCond != null) {
                        lastConstraint().setCorrelationCondition(corrCond);
                        System.out.println("Parsed condition: " + corrCond.toRuMString());
                    } else {
                        System.out.println("Unable to parse correlation condition");
                    }
                    break;
                case TIME:
                    TimeCondition timeCond = ConditionParser.parseTimeCondition(transcript);
                    if (timeCond != null) {
                        lastConstraint().setTimeCondition(timeCond);
                        System.out.println("Parsed condition: " + timeCond.toRuMString());
                    } else {
                        System.out.println("Unable to parse time condition");
                    }
                    break;
            }

            recordingCondition = null;
            System.out.println(getCommands());
            return;
        }

        switch (transcript) {
            case "record":
            case "create":
                recordingRule = true;
                System.out.println("Now recording constraints...");
                return;
            case "close":
                handleEnding();
                return;

            //Last constraint based
            case "delete":
                int lastIndex = recordedConstraints.size() - 1;
                if (lastIndex >= 0) {
                    DeclareConstraint rule = recordedConstraints.remove(lastIndex);
                    System.out.println("Removed constraint: \"" + rule.toRuMString() + "\"");
                }
                break;
            case "activation condition":
                if (recordedConstraints.size() > 0) {
                    recordingCondition = Condition.ConditionType.ACTIVATION;
                    System.out.println("Now recording activation condition...");
                    System.out.println(actCondExample);
                    return;
                }
                break;
            case "correlation condition":
                if (recordedConstraints.size() > 0) {
                    recordingCondition = Condition.ConditionType.CORRELATION;
                    System.out.println("Now recording correlation condition...");
                    System.out.println(corrCondExample);
                    return;
                }
                break;
            case "time condition":
                if (recordedConstraints.size() > 0) {
                    recordingCondition = Condition.ConditionType.TIME;
                    System.out.println("Now recording time condition...");
                    System.out.println(timeCondExample);
                    return;
                }
                break;
            default:
                System.out.println("Unknown command");
        }
        System.out.println(getCommands());
    }

    private static void handleEnding() {
        keepOnlistening = false;
        System.out.println("Goodbye! We hope you enjoyed.");
        if (recordedConstraints.isEmpty()) {
            return;
        }

        List<String> actions = new ArrayList<>();
        List<String> constraints = new ArrayList<>();

        for (DeclareConstraint constraint : recordedConstraints) {
            constraints.add(constraint.toRuMString());
            if (constraint.getActionA() != null) {
                actions.add(constraint.getActionA().baseStr());
            }
            if (constraint.getActionB() != null) {
                actions.add(constraint.getActionB().baseStr());
            }
        }

        System.out.println("Recorded actions:");
        for (String s : actions) {
            System.out.println("\t" + s);
        }

        System.out.println("Recorded constraints:");
        for (String s : constraints) {
            System.out.println("\t" + s);
        }

        System.out.println("Writing to file...");
        DeclareUtil.writeToFile(actions, constraints);
        System.out.println("Done! You can now close the program.");
    }

    private static DeclareConstraint lastConstraint () {
        int lastIndex = recordedConstraints.size() - 1;
        if (lastIndex >= 0) {
            return recordedConstraints.get(lastIndex);
        }

        return null;
    }

    private static String getCommands() {
        if (recordedConstraints.size() == 0) {
            return "\n" + availableCommands;
        } else {
            return "\n" + availableCommands + "\n" + constraintCommands + "\n" + "Last recorded: " + lastConstraint().toRuMString();
        }
    }
}
