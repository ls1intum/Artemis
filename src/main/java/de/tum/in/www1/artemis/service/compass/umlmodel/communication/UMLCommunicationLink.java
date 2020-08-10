package de.tum.in.www1.artemis.service.compass.umlmodel.communication;

import java.util.List;
import java.util.Objects;

import de.tum.in.www1.artemis.service.compass.umlmodel.Similarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration;

public class UMLCommunicationLink extends UMLElement {

    public final static String UML_COMMUNICATION_LINK_TYPE = "CommunicationLink";

    private UMLObject source;

    private UMLObject target;

    private List<UMLMessage> messages;

    public UMLCommunicationLink(UMLObject source, UMLObject target, List<UMLMessage> messages, String jsonElementID) {
        super(jsonElementID);

        this.source = source;
        this.target = target;
        this.messages = messages;
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        if (!(reference instanceof UMLCommunicationLink)) {
            return 0;
        }

        UMLCommunicationLink referenceLink = (UMLCommunicationLink) reference;

        double similarity = 0;

        similarity += referenceLink.getSource().similarity(source) * CompassConfiguration.RELATION_ELEMENT_WEIGHT;
        similarity += referenceLink.getTarget().similarity(target) * CompassConfiguration.RELATION_ELEMENT_WEIGHT;

        // the remaining 50% are taken into account for the messages
        double messageWeight = (1 - 2 * CompassConfiguration.RELATION_ELEMENT_WEIGHT) / messages.size();

        for (var message : messages) {
            double similarityValue = referenceLink.messages.stream().mapToDouble(referenceLinkMessage -> referenceLinkMessage.similarity(message)).max().orElse(0);
            similarity += messageWeight * similarityValue;
        }

        return ensureSimilarityRange(similarity);
    }

    @Override
    public String toString() {
        return "CommunicationLink " + getSource().getName() + "->" + getTarget().getName() + " (" + messages + ")";
    }

    @Override
    public String getName() {
        return getType();
    }

    @Override
    public String getType() {
        return UML_COMMUNICATION_LINK_TYPE;
    }

    public UMLObject getSource() {
        return source;
    }

    public UMLObject getTarget() {
        return target;
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        UMLCommunicationLink otherRelationship = (UMLCommunicationLink) obj;

        return Objects.equals(otherRelationship.getSource(), source) && Objects.equals(otherRelationship.getTarget(), target)
                && Objects.equals(otherRelationship.messages, messages);
    }
}
