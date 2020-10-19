package de.tum.in.www1.artemis.service.compass.umlmodel.syntaxtree;

import java.util.ArrayList;
import java.util.List;

import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

public class SyntaxTree extends UMLDiagram {

    private final List<SyntaxTreeNonterminal> nonterminals;

    private final List<SyntaxTreeTerminal> terminals;

    private final List<SyntaxTreeLink> links;

    public SyntaxTree(long modelSubmissionId, List<SyntaxTreeNonterminal> nonterminals, List<SyntaxTreeTerminal> terminals, List<SyntaxTreeLink> links) {
        super(modelSubmissionId);
        this.nonterminals = nonterminals;
        this.terminals = terminals;
        this.links = links;
    }

    @Override
    public UMLElement getElementByJSONID(String jsonElementId) {
        for (SyntaxTreeNonterminal nonterminal : getNonterminals()) {
            if (nonterminal.getJSONElementID().equals(jsonElementId)) {
                return nonterminal;
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

    public List<SyntaxTreeNonterminal> getNonterminals() {
        return nonterminals;
    }

    public List<SyntaxTreeTerminal> getTerminals() {
        return terminals;
    }

    public List<SyntaxTreeLink> getLinks() {
        return links;
    }

    @Override
    protected List<UMLElement> getModelElements() {
        List<UMLElement> modelElements = new ArrayList<>();
        modelElements.addAll(nonterminals);
        modelElements.addAll(terminals);
        modelElements.addAll(links);
        return modelElements;
    }
}
