package de.tum.in.www1.artemis.service.compass.umlmodel.syntaxtree;

import de.tum.in.www1.artemis.service.compass.strategy.NameSimilarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.Similarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

public class SyntaxTreeTerminal extends UMLElement {

    public static final String SYNTAX_TREE_TERMINAL_TYPE = "SyntaxTreeTerminal";

    private final String name;

    public SyntaxTreeTerminal(String name, String jsonElementID) {
        super(jsonElementID);
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return SYNTAX_TREE_TERMINAL_TYPE;
    }

    @Override
    public String toString() {
        return "SyntaxTreeTerminal " + name;
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        double similarity = 0;

        if (!(reference instanceof SyntaxTreeTerminal)) {
            return similarity;
        }
        SyntaxTreeTerminal referenceTerminal = (SyntaxTreeTerminal) reference;

        similarity += NameSimilarity.levenshteinSimilarity(getName(), referenceTerminal.getName());

        return ensureSimilarityRange(similarity);
    }

    /**
     * Checks for overall similarity including attributes and methods.
     *
     * @param reference the reference element to compare this object with
     * @return the similarity as number [0-1]
     */
    @Override
    public double overallSimilarity(Similarity<UMLElement> reference) {
        if (!(reference instanceof SyntaxTreeTerminal)) {
            return 0;
        }

        SyntaxTreeTerminal referenceTerminal = (SyntaxTreeTerminal) reference;

        double similarity = similarity(referenceTerminal);

        return ensureSimilarityRange(similarity);
    }
}
