package de.tum.cit.aet.artemis.service.compass.umlmodel.communication;

import de.tum.cit.aet.artemis.service.compass.strategy.NameSimilarity;

public class UMLMessage {

    private final String name;

    private final Direction direction;

    public UMLMessage(String name, Direction direction) {
        this.name = name;
        this.direction = direction;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return "UMLMessage";
    }

    @Override
    public String toString() {
        return name + ":" + direction;
    }

    public double similarity(UMLMessage otherMessage) {
        double similarity = 0.9 * NameSimilarity.levenshteinSimilarity(getName(), otherMessage.getName());
        return similarity + (direction.equals(otherMessage.direction) ? 0.1 : 0.0);
    }
}
