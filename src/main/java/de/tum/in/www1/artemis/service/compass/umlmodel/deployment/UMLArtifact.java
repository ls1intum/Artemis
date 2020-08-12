package de.tum.in.www1.artemis.service.compass.umlmodel.deployment;

import de.tum.in.www1.artemis.service.compass.strategy.NameSimilarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.Similarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

public class UMLArtifact extends UMLElement {

    public final static String UML_ARTIFACT_TYPE = "Artifact";

    private final String name;

    public UMLArtifact(String name, String jsonElementID) {
        super(jsonElementID);
        this.name = name;
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        double similarity = 0;

        if (reference instanceof UMLArtifact) {
            UMLArtifact referenceArtifact = (UMLArtifact) reference;
            similarity += NameSimilarity.levenshteinSimilarity(name, referenceArtifact.getName());
        }

        return ensureSimilarityRange(similarity);
    }

    @Override
    public String toString() {
        return "Artifact " + name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return UML_ARTIFACT_TYPE;
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        UMLArtifact otherArtifact = (UMLArtifact) obj;

        return otherArtifact.name.equals(this.name);
    }
}
