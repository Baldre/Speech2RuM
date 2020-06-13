package org.beldr;

import declareextraction.constructs.DeclareModel;
import declareextraction.constructs.TextModel;
import declareextraction.textprocessing.DeclareConstructor;
import declareextraction.textprocessing.TextParser;

import java.io.*;
import java.util.List;

public class DeclareUtil {

    private static final String FILENAME = "model.decl";

    /**
     * @deprecated Should only be used internally for standalone, uses 1.0a of DeclareExtractor
     */
    @Deprecated () //Should only be used internally
    public static DeclareModel generateModel(String input) {
        //DeclareExtractor#runSingleConstraint
        PrintStream ogOut = System.out;
        PrintStream ogErr = System.err;
        PrintStream voidStream = new PrintStream(new OutputStream() {
            public void write(int b) {
            }
        });
        System.setOut(voidStream);
        System.setErr(voidStream); //Can just disable logging when working on the project
        TextParser parser = new TextParser();
        DeclareConstructor declareConstructor = new DeclareConstructor();
        TextModel textModel = parser.parseConstraintString(input);
        DeclareModel dm = declareConstructor.convertToDeclareModel(textModel);
        dm.addTextModel(textModel);
        System.setOut(ogOut);
        System.setErr(ogErr);
        return dm;
    }

    public static void writeToFile(List<String> actions, List<String> constraints) {
        File file = new File(FILENAME);
        try (PrintWriter out = new PrintWriter(file)) {
            for (String action : actions) {
                out.println("activity " + action);
            }
            for (String constraint: constraints) {
                out.println(constraint);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
