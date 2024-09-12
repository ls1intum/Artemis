package de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.parsers.v2;

import static de.tum.cit.aet.artemis.modeling.service.compass.utils.JSONMapping.ELEMENT_ID;
import static de.tum.cit.aet.artemis.modeling.service.compass.utils.JSONMapping.ELEMENT_NAME;
import static de.tum.cit.aet.artemis.modeling.service.compass.utils.JSONMapping.ELEMENT_TYPE;
import static de.tum.cit.aet.artemis.modeling.service.compass.utils.JSONMapping.RELATIONSHIP_SOURCE;
import static de.tum.cit.aet.artemis.modeling.service.compass.utils.JSONMapping.RELATIONSHIP_TARGET;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.UMLElement;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.parsers.UMLModelParser;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.petrinet.PetriNet;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.petrinet.PetriNetArc;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.petrinet.PetriNetPlace;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.petrinet.PetriNetTransition;

public class PetriNetParser {

    /**
     * Create a petri net from the model and relationship elements given as JSON arrays. It parses the JSON objects to corresponding Java objects and creates a
     * petri net containing these UML model elements.
     *
     * @param modelElements     the model elements as JSON array
     * @param relationships     the relationship elements as JSON array
     * @param modelSubmissionId the ID of the corresponding modeling submission
     * @return a petri net containing the parsed model elements and relationships
     * @throws IOException when no corresponding model elements could be found for the source and target IDs in the relationship JSON objects
     */
    protected static PetriNet buildPetriNetFromJSON(JsonArray modelElements, JsonArray relationships, long modelSubmissionId) throws IOException {
        List<PetriNetArc> arcs = new ArrayList<>();
        Map<String, PetriNetPlace> places = new HashMap<>();
        Map<String, PetriNetTransition> transitions = new HashMap<>();
        Map<String, UMLElement> allElementsMap = new HashMap<>();

        // loop over all JSON elements and create the UML objects
        for (JsonElement jsonElement : modelElements) {
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            String elementType = jsonObject.get(ELEMENT_TYPE).getAsString();
            // elementType is never null
            switch (elementType) {
                case PetriNetPlace.PETRI_NET_PLACE_TYPE -> {
                    PetriNetPlace place = parsePetriNetPlace(jsonObject);
                    places.put(place.getJSONElementID(), place);
                    allElementsMap.put(place.getJSONElementID(), place);
                }
                case PetriNetTransition.PETRI_NET_TRANSITION_TYPE -> {
                    PetriNetTransition transition = parsePetriNetTransition(jsonObject);
                    transitions.put(transition.getJSONElementID(), transition);
                    allElementsMap.put(transition.getJSONElementID(), transition);
                }
                default -> {
                    // ignore unknown elements
                }
            }
        }

        // loop over all JSON control flow elements and create syntax tree links
        for (JsonElement rel : relationships) {
            Optional<PetriNetArc> petriNetArc = parsePetriNetArc(rel.getAsJsonObject(), allElementsMap);
            petriNetArc.ifPresent(arcs::add);
        }

        return new PetriNet(modelSubmissionId, List.copyOf(places.values()), List.copyOf(transitions.values()), arcs);
    }

    /**
     * Parses the given JSON representation of a UML petri net place to a PetriNetPlace Java object.
     *
     * @param componentJson the JSON object containing the petri net place
     * @return the PetriNetPlace object parsed from the JSON object
     */
    private static PetriNetPlace parsePetriNetPlace(JsonObject componentJson) {
        String name = componentJson.get(ELEMENT_NAME).getAsString();
        String amountOfTokens = componentJson.get("amountOfTokens").getAsString();
        String capacity = componentJson.get("capacity").getAsString();
        return new PetriNetPlace(name, amountOfTokens, capacity, componentJson.get(ELEMENT_ID).getAsString());
    }

    /**
     * Parses the given JSON representation of a UML petri net transition to a PetriNetTransition Java object.
     *
     * @param componentJson the JSON object containing the petri net transition
     * @return the PetriNetTransition object parsed from the JSON object
     */
    private static PetriNetTransition parsePetriNetTransition(JsonObject componentJson) {
        String name = componentJson.get(ELEMENT_NAME).getAsString();
        return new PetriNetTransition(name, componentJson.get(ELEMENT_ID).getAsString());
    }

    /**
     * Parses the given JSON representation of a UML relationship to a PetriNetArc Java object.
     *
     * @param relationshipJson      the JSON object containing the relationship
     * @param allSyntaxTreeElements a map containing all objects of the corresponding syntax tree, necessary for assigning source and target element of the relationships
     * @return the PetriNetArc object parsed from the JSON object
     * @throws IOException when no class could be found in the classMap for the source and target ID in the JSON object
     */
    private static Optional<PetriNetArc> parsePetriNetArc(JsonObject relationshipJson, Map<String, UMLElement> allSyntaxTreeElements) throws IOException {
        String multiplicity = relationshipJson.get(ELEMENT_NAME).getAsString();
        UMLElement source = UMLModelParser.findElement(relationshipJson, allSyntaxTreeElements, RELATIONSHIP_SOURCE);
        UMLElement target = UMLModelParser.findElement(relationshipJson, allSyntaxTreeElements, RELATIONSHIP_TARGET);

        if (source == null || target == null) {
            throw new IOException("Relationship source or target not part of model!");
        }
        PetriNetArc newPetriNetArc = new PetriNetArc(multiplicity, source, target, relationshipJson.get(ELEMENT_ID).getAsString());
        return Optional.of(newPetriNetArc);
    }

}
