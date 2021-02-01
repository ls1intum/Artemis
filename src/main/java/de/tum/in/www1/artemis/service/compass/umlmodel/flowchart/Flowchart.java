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

    private final List<FlowchartFlowline> flowlines;

    public Flowchart(long modelSubmissionId, List<FlowchartTerminal> terminals, List<FlowchartProcess> processes, List<FlowchartDecision> decisions,
            List<FlowchartInputOutput> inputOutputs, List<FlowchartFunctionCall> functionCalls, List<FlowchartFlowline> flowlines) {
        super(modelSubmissionId);
        this.terminals = terminals;
        this.processes = processes;
        this.decisions = decisions;
        this.inputOutputs = inputOutputs;
        this.functionCalls = functionCalls;
        this.flowlines = flowlines;
    }

    @Override
    public UMLElement getElementByJSONID(String jsonElementId) {

        for (UMLElement element : getModelElements()) {
            if (element.getJSONElementID().equals(jsonElementId)) {
                return element;
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

    public List<FlowchartFlowline> getFlowlines() {
        return flowlines;
    }

    @Override
    protected List<UMLElement> getModelElements() {
        List<UMLElement> modelElements = new ArrayList<>();
        modelElements.addAll(terminals);
        modelElements.addAll(processes);
        modelElements.addAll(decisions);
        modelElements.addAll(inputOutputs);
        modelElements.addAll(functionCalls);
        modelElements.addAll(flowlines);
        return modelElements;
    }
}
