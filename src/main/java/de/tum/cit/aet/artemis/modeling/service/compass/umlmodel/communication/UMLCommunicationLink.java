package de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.communication;

import java.util.List;
import java.util.Objects;

import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.Similarity;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.UMLElement;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.object.UMLObject;
import de.tum.cit.aet.artemis.modeling.service.compass.utils.CompassConfiguration;

public class UMLCommunicationLink extends UMLElement {

    public static final String UML_COMMUNICATION_LINK_TYPE = "CommunicationLink";

    private final UMLObject source;

    private final UMLObject target;

    private final List<UMLMessage> messages;

    public UMLCommunicationLink(UMLObject source, UMLObject target, List<UMLMessage> messages, String jsonElementID) {
        super(jsonElementID);

        this.source = source;
        this.target = target;
        this.messages = messages;
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        if (!(reference instanceof UMLCommunicationLink referenceLink)) {
            return 0;
        }

        double similarity = 0;

        double sourceWeight = CompassConfiguration.RELATION_ELEMENT_WEIGHT;
        double targetWeight = CompassConfiguration.RELATION_ELEMENT_WEIGHT;

        // when there are no messages, we increase the weight so that the links can still have a similarity of 100%
        if (messages.isEmpty()) {
            sourceWeight = 0.5;
            targetWeight = 0.5;
        }

        similarity += referenceLink.getSource().similarity(source) * sourceWeight;
        similarity += referenceLink.getTarget().similarity(target) * targetWeight;

        if (!messages.isEmpty()) {
            // the remaining 50% are taken into account for the messages
            double messageWeight = (1 - 2 * CompassConfiguration.RELATION_ELEMENT_WEIGHT) / messages.size();

            for (var message : messages) {
                double similarityValue = referenceLink.messages.stream().mapToDouble(referenceLinkMessage -> referenceLinkMessage.similarity(message)).max().orElse(0);
                similarity += messageWeight * similarityValue;
            }
        }

        return ensureSimilarityRange(similarity);
    }

    @Override
    public String toString() {
        return "CommunicationLink " + getSource().getName() + " -> " + getTarget().getName() + " (" + messages + ")";
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
    public int hashCode() {
        return Objects.hash(super.hashCode(), source, target, messages);
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
