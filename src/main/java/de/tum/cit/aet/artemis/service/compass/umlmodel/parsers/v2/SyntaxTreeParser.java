package de.tum.cit.aet.artemis.service.compass.umlmodel.parsers.v2;

import static de.tum.cit.aet.artemis.service.compass.utils.JSONMapping.ELEMENT_ID;
import static de.tum.cit.aet.artemis.service.compass.utils.JSONMapping.ELEMENT_NAME;
import static de.tum.cit.aet.artemis.service.compass.utils.JSONMapping.ELEMENT_TYPE;
import static de.tum.cit.aet.artemis.service.compass.utils.JSONMapping.RELATIONSHIP_SOURCE;
import static de.tum.cit.aet.artemis.service.compass.utils.JSONMapping.RELATIONSHIP_TARGET;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import de.tum.cit.aet.artemis.service.compass.umlmodel.UMLElement;
import de.tum.cit.aet.artemis.service.compass.umlmodel.parsers.UMLModelParser;
import de.tum.cit.aet.artemis.service.compass.umlmodel.syntaxtree.SyntaxTree;
import de.tum.cit.aet.artemis.service.compass.umlmodel.syntaxtree.SyntaxTreeLink;
import de.tum.cit.aet.artemis.service.compass.umlmodel.syntaxtree.SyntaxTreeNonterminal;
import de.tum.cit.aet.artemis.service.compass.umlmodel.syntaxtree.SyntaxTreeTerminal;

public class SyntaxTreeParser {

    /**
     * Create a syntax tree from the model and relationship elements given as JSON arrays. It parses the JSON objects to corresponding Java objects and creates a
     * syntax tree containing these UML model elements.
     *
     * @param modelElements     the model elements as JSON array
     * @param relationships     the relationship elements as JSON array
     * @param modelSubmissionId the ID of the corresponding modeling submission
     * @return a syntax tree containing the parsed model elements and relationships
     * @throws IOException when no corresponding model elements could be found for the source and target IDs in the relationship JSON objects
     */
    protected static SyntaxTree buildSyntaxTreeFromJSON(JsonArray modelElements, JsonArray relationships, long modelSubmissionId) throws IOException {
        List<SyntaxTreeLink> syntaxTreeLinkList = new ArrayList<>();
        Map<String, SyntaxTreeTerminal> terminalMap = new HashMap<>();
        Map<String, SyntaxTreeNonterminal> nonTerminalMap = new HashMap<>();
        Map<String, UMLElement> allElementsMap = new HashMap<>();

        // loop over all JSON elements and create the UML objects
        for (JsonElement jsonElement : modelElements) {
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            String elementType = jsonObject.get(ELEMENT_TYPE).getAsString();
            // elementType is never null
            switch (elementType) {
                case SyntaxTreeTerminal.SYNTAX_TREE_TERMINAL_TYPE -> {
                    SyntaxTreeTerminal terminal = parseTerminal(jsonObject);
                    terminalMap.put(terminal.getJSONElementID(), terminal);
                    allElementsMap.put(terminal.getJSONElementID(), terminal);
                }
                case SyntaxTreeNonterminal.SYNTAX_TREE_NONTERMINAL_TYPE -> {
                    SyntaxTreeNonterminal nonTerminal = parseNonTerminal(jsonObject);
                    nonTerminalMap.put(nonTerminal.getJSONElementID(), nonTerminal);
                    allElementsMap.put(nonTerminal.getJSONElementID(), nonTerminal);
                }
                default -> {
                    // ignore unknown elements
                }
            }
        }

        // loop over all JSON control flow elements and create syntax tree links
        for (JsonElement rel : relationships) {
            Optional<SyntaxTreeLink> syntaxTreeLink = parseSyntaxTreeLink(rel.getAsJsonObject(), allElementsMap);
            syntaxTreeLink.ifPresent(syntaxTreeLinkList::add);
        }

        return new SyntaxTree(modelSubmissionId, List.copyOf(nonTerminalMap.values()), List.copyOf(terminalMap.values()), syntaxTreeLinkList);
    }

    /**
     * Parses the given JSON representation of a UML terminal to a SyntaxTreeTerminal Java object.
     *
     * @param componentJson the JSON object containing the terminal
     * @return the SyntaxTreeTerminal object parsed from the JSON object
     */
    private static SyntaxTreeTerminal parseTerminal(JsonObject componentJson) {
        String name = componentJson.get(ELEMENT_NAME).getAsString();
        return new SyntaxTreeTerminal(name, componentJson.get(ELEMENT_ID).getAsString());
    }

    /**
     * Parses the given JSON representation of a UML nonTerminal to a SyntaxTreeNonTerminal Java object.
     *
     * @param componentJson the JSON object containing the nonTerminal
     * @return the SyntaxTreeNonTerminal object parsed from the JSON object
     */
    private static SyntaxTreeNonterminal parseNonTerminal(JsonObject componentJson) {
        String name = componentJson.get(ELEMENT_NAME).getAsString();
        return new SyntaxTreeNonterminal(name, componentJson.get(ELEMENT_ID).getAsString());
    }

    /**
     * Parses the given JSON representation of a UML relationship to a SyntaxTreeLink Java object.
     *
     * @param relationshipJson      the JSON object containing the relationship
     * @param allSyntaxTreeElements a map containing all objects of the corresponding syntax tree, necessary for assigning source and target element of the relationships
     * @return the SyntaxTreeLink object parsed from the JSON object
     * @throws IOException when no class could be found in the classMap for the source and target ID in the JSON object
     */
    private static Optional<SyntaxTreeLink> parseSyntaxTreeLink(JsonObject relationshipJson, Map<String, UMLElement> allSyntaxTreeElements) throws IOException {
        UMLElement source = UMLModelParser.findElement(relationshipJson, allSyntaxTreeElements, RELATIONSHIP_SOURCE);
        UMLElement target = UMLModelParser.findElement(relationshipJson, allSyntaxTreeElements, RELATIONSHIP_TARGET);

        if (source == null || target == null) {
            throw new IOException("Relationship source or target not part of model!");
        }
        SyntaxTreeLink newSyntaxTreeLink = new SyntaxTreeLink(source, target, relationshipJson.get(ELEMENT_ID).getAsString());
        return Optional.of(newSyntaxTreeLink);
    }

}
