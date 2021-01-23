package de.tum.in.www1.artemis.service.compass.umlmodel.flowchart;

import java.util.ArrayList;
import java.util.List;

import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

public class Flowchart extends UMLDiagram {

    private final List<FlowchartTerminal> terminals;

    private final List<FlowchartProcess> processes;

    private final List<FlowchartDecision> decisions;

    private final List<FlowchartInputOutput> inputOutputs;

    private final List<FlowchartFunctionCall> functionCalls;

    private final List<FlowchartFlowline> links;

    public Flowchart(long modelSubmissionId, List<FlowchartTerminal> terminals, List<FlowchartProcess> processes, List<FlowchartDecision> decisions,
            List<FlowchartInputOutput> inputOutputs, List<FlowchartFunctionCall> functionCalls, List<FlowchartFlowline> links) {
        super(modelSubmissionId);
        this.terminals = terminals;
        this.processes = processes;
        this.decisions = decisions;
        this.inputOutputs = inputOutputs;
        this.functionCalls = functionCalls;
        this.links = links;
    }

    @Override
    public UMLElement getElementByJSONID(String jsonElementId) {
        for (FlowchartTerminal terminal : getTerminals()) {
            if (terminal.getJSONElementID().equals(jsonElementId)) {
                return terminal;
            }
        }

        for (FlowchartProcess process : getProcesses()) {
            if (process.getJSONElementID().equals(jsonElementId)) {
                return process;
            }
        }

        for (FlowchartDecision decision : getDecisions()) {
            if (decision.getJSONElementID().equals(jsonElementId)) {
                return decision;
            }
        }

        for (FlowchartInputOutput inputOutput : getInputOutputs()) {
            if (inputOutput.getJSONElementID().equals(jsonElementId)) {
                return inputOutput;
            }
        }

        for (FlowchartFunctionCall functionCall : getFunctionCalls()) {
            if (functionCall.getJSONElementID().equals(jsonElementId)) {
                return functionCall;
            }
        }

        for (FlowchartFlowline link : getLinks()) {
            if (link.getJSONElementID().equals(jsonElementId)) {
                return link;
            }
        }
        return null;
    }

    public List<FlowchartTerminal> getTerminals() {
        return terminals;
    }

    public List<FlowchartProcess> getProcesses() {
        return processes;
    }

    public List<FlowchartDecision> getDecisions() {
        return decisions;
    }

    public List<FlowchartInputOutput> getInputOutputs() {
        return inputOutputs;
    }

    public List<FlowchartFunctionCall> getFunctionCalls() {
        return functionCalls;
    }

    public List<FlowchartFlowline> getLinks() {
        return links;
    }

    @Override
    protected List<UMLElement> getModelElements() {
        List<UMLElement> modelElements = new ArrayList<>();
        modelElements.addAll(terminals);
        modelElements.addAll(processes);
        modelElements.addAll(links);
        return modelElements;
    }
}
