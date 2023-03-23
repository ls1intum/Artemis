package de.tum.in.www1.artemis.service.compass.umlmodel.syntaxtree;

import java.util.ArrayList;
import java.util.List;

import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

public class SyntaxTree extends UMLDiagram {

    private final List<SyntaxTreeNonterminal> nonTerminals;

    private final List<SyntaxTreeTerminal> terminals;

    private final List<SyntaxTreeLink> links;

    public SyntaxTree(long modelSubmissionId, List<SyntaxTreeNonterminal> nonTerminals, List<SyntaxTreeTerminal> terminals, List<SyntaxTreeLink> links) {
        super(modelSubmissionId);
        this.nonTerminals = nonTerminals;
        this.terminals = terminals;
        this.links = links;
    }

    @Override
    public UMLElement getElementByJSONID(String jsonElementId) {
        for (SyntaxTreeNonterminal nonTerminal : getNonTerminals()) {
            if (nonTerminal.getJSONElementID().equals(jsonElementId)) {
                return nonTerminal;
            }
        }
        for (SyntaxTreeTerminal terminal : getTerminals()) {
            if (terminal.getJSONElementID().equals(jsonElementId)) {
                return terminal;
            }
        }
        for (SyntaxTreeLink link : getLinks()) {
            if (link.getJSONElementID().equals(jsonElementId)) {
                return link;
            }
        }
        return null;
    }

    public List<SyntaxTreeNonterminal> getNonTerminals() {
        return nonTerminals;
    }

    public List<SyntaxTreeTerminal> getTerminals() {
        return terminals;
    }

    public List<SyntaxTreeLink> getLinks() {
        return links;
    }

    @Override
    public List<UMLElement> getModelElements() {
        List<UMLElement> modelElements = new ArrayList<>();
        modelElements.addAll(nonTerminals);
        modelElements.addAll(terminals);
        modelElements.addAll(links);
        return modelElements;
    }
}
