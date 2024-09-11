package de.tum.cit.aet.artemis.service.compass.umlmodel.bpmn;

import java.util.ArrayList;
import java.util.List;

import de.tum.cit.aet.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.cit.aet.artemis.service.compass.umlmodel.UMLElement;

/**
 * Represents a BPMN diagram
 */
public class BPMNDiagram extends UMLDiagram {

    private final List<BPMNAnnotation> annotations;

    private final List<BPMNCallActivity> callActivities;

    private final List<BPMNDataObject> dataObjects;

    private final List<BPMNDataStore> dataStores;

    private final List<BPMNEndEvent> endEvents;

    private final List<BPMNGateway> gateways;

    private final List<BPMNGroup> groups;

    private final List<BPMNIntermediateEvent> intermediateEvents;

    private final List<BPMNPool> pools;

    private final List<BPMNStartEvent> startEvents;

    private final List<BPMNSubprocess> subprocesses;

    private final List<BPMNSwimlane> swimlanes;

    private final List<BPMNTask> tasks;

    private final List<BPMNTransaction> transactions;

    private final List<BPMNFlow> flows;

    /**
     * Construct an instance of the BPMNDiagram class
     *
     * @param modelSubmissionId  The ID of the submission this diagram instance was submitted for
     * @param annotations        The BPMNAnnotation elements contained in this diagram
     * @param callActivities     The BPMNCallActivity elements contained in this diagram
     * @param dataObjects        The BPMNDataObject elements contained in this diagram
     * @param dataStores         The BPMNDataStore elements contained in this diagram
     * @param endEvents          The BPMNEndEvent elements contained in this diagram
     * @param gateways           The BPMNGateway elements contained in this diagram
     * @param groups             The BPMNGroup elements contained in this diagram
     * @param intermediateEvents The BPMNIntermediateEvent elements contained in this diagram
     * @param pools              The BPMNPool elements contained in this diagram
     * @param startEvents        The BPMNStartEvent elements contained in this diagram
     * @param subprocesses       The BPMNSubprocess elements contained in this diagram
     * @param swimlanes          The BPMNSwimlane elements contained in this diagram
     * @param tasks              The BPMNTask elements contained in this diagram
     * @param transactions       The BPMNTransaction elements contained in this diagram
     * @param flows              The BPMNFlow elements contained in this diagram
     */
    public BPMNDiagram(long modelSubmissionId, List<BPMNAnnotation> annotations, List<BPMNCallActivity> callActivities, List<BPMNDataObject> dataObjects,
            List<BPMNDataStore> dataStores, List<BPMNEndEvent> endEvents, List<BPMNGateway> gateways, List<BPMNGroup> groups, List<BPMNIntermediateEvent> intermediateEvents,
            List<BPMNPool> pools, List<BPMNStartEvent> startEvents, List<BPMNSubprocess> subprocesses, List<BPMNSwimlane> swimlanes, List<BPMNTask> tasks,
            List<BPMNTransaction> transactions, List<BPMNFlow> flows) {
        super(modelSubmissionId);
        this.annotations = annotations;
        this.callActivities = callActivities;
        this.dataObjects = dataObjects;
        this.dataStores = dataStores;
        this.endEvents = endEvents;
        this.gateways = gateways;
        this.groups = groups;
        this.intermediateEvents = intermediateEvents;
        this.pools = pools;
        this.startEvents = startEvents;
        this.subprocesses = subprocesses;
        this.swimlanes = swimlanes;
        this.tasks = tasks;
        this.transactions = transactions;
        this.flows = flows;
    }

    /**
     * Retrieve a diagram element via its JSON ID
     *
     * @param jsonElementId the id of the UML element
     * @return The UMLElement corresponding to the given JSON ID
     */
    @Override
    public UMLElement getElementByJSONID(String jsonElementId) {

        for (UMLElement element : getModelElements()) {
            if (element.getJSONElementID().equals(jsonElementId)) {
                return element;
            }
        }

        return null;
    }

    /**
     * Get all first-level model elements. As we do not support second-level elements for BPMN
     * diagrams as for example methods and attributes on class diagrams, this method simply
     * returns all diagram elements.
     *
     * @return All elements in the diagram
     */
    @Override
    public List<UMLElement> getModelElements() {
        List<UMLElement> modelElements = new ArrayList<>();
        modelElements.addAll(annotations);
        modelElements.addAll(callActivities);
        modelElements.addAll(dataObjects);
        modelElements.addAll(dataStores);
        modelElements.addAll(endEvents);
        modelElements.addAll(gateways);
        modelElements.addAll(groups);
        modelElements.addAll(intermediateEvents);
        modelElements.addAll(pools);
        modelElements.addAll(startEvents);
        modelElements.addAll(subprocesses);
        modelElements.addAll(swimlanes);
        modelElements.addAll(tasks);
        modelElements.addAll(transactions);
        modelElements.addAll(flows);
        return modelElements;
    }

    /**
     * Retrieve annotations from the diagram
     *
     * @return All BPMNAnnotation elements in the diagrams
     */
    public List<BPMNAnnotation> getAnnotations() {
        return annotations;
    }

    /**
     * Retrieve call activities from the diagram
     *
     * @return All BPMNCallActivity elements in the diagrams
     */
    public List<BPMNCallActivity> getCallActivities() {
        return callActivities;
    }

    /**
     * Retrieve data objects from the diagram
     *
     * @return All BPMNDataObject elements in the diagrams
     */
    public List<BPMNDataObject> getDataObjects() {
        return dataObjects;
    }

    /**
     * Retrieve data stores from the diagram
     *
     * @return All BPMNDataStore elements in the diagrams
     */
    public List<BPMNDataStore> getDataStores() {
        return dataStores;
    }

    /**
     * Retrieve end events from the diagram
     *
     * @return All BPMNEndEvent elements in the diagrams
     */
    public List<BPMNEndEvent> getEndEvents() {
        return endEvents;
    }

    /**
     * Retrieve gateways from the diagram
     *
     * @return All BPMNGateway elements in the diagrams
     */
    public List<BPMNGateway> getGateways() {
        return gateways;
    }

    /**
     * Retrieve groups from the diagram
     *
     * @return All BPMNGroup elements in the diagrams
     */
    public List<BPMNGroup> getGroups() {
        return groups;
    }

    /**
     * Retrieve intermediate events from the diagram
     *
     * @return All BPMNIntermediateEvent elements in the diagrams
     */
    public List<BPMNIntermediateEvent> getIntermediateEvents() {
        return intermediateEvents;
    }

    /**
     * Retrieve pools from the diagram
     *
     * @return All BPMNPool elements in the diagrams
     */
    public List<BPMNPool> getPools() {
        return pools;
    }

    /**
     * Retrieve start events from the diagram
     *
     * @return All BPMNStartEvent elements in the diagrams
     */
    public List<BPMNStartEvent> getStartEvents() {
        return startEvents;
    }

    /**
     * Retrieve subprocesses from the diagram
     *
     * @return All BPMNSubprocess elements in the diagrams
     */
    public List<BPMNSubprocess> getSubprocesses() {
        return subprocesses;
    }

    /**
     * Retrieve swimlanes from the diagram
     *
     * @return All BPMNSwimlane elements in the diagrams
     */
    public List<BPMNSwimlane> getSwimlanes() {
        return swimlanes;
    }

    /**
     * Retrieve tasks from the diagram
     *
     * @return All BPMNTask elements in the diagrams
     */
    public List<BPMNTask> getTasks() {
        return tasks;
    }

    /**
     * Retrieve transactions from the diagram
     *
     * @return All BPMNTransaction elements in the diagrams
     */
    public List<BPMNTransaction> getTransactions() {
        return transactions;
    }

    /**
     * Retrieve flows from the diagram
     *
     * @return All BPMNFlow elements in the diagrams
     */
    public List<BPMNFlow> getFlows() {
        return flows;
    }
}
