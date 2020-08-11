package de.tum.in.www1.artemis.service.compass.umlmodel.usecase;

import java.util.ArrayList;
import java.util.List;

import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

public class UMLUseCaseDiagram extends UMLDiagram {

    // TODO recreate for the purpose of UML Component diagrams

    private final List<UMLSystemBoundary> systemBoundaryList;

    private final List<UMLActor> actorList;

    private final List<UMLUseCase> useCaseList;

    private final List<UMLUseCaseAssociation> useCaseAssociationList;

    public UMLUseCaseDiagram(long modelSubmissionId, List<UMLSystemBoundary> systemBoundaryList, List<UMLActor> actorList, List<UMLUseCase> useCaseList,
            List<UMLUseCaseAssociation> useCaseAssociationList) {
        super(modelSubmissionId);
        this.systemBoundaryList = systemBoundaryList;
        this.actorList = actorList;
        this.useCaseList = useCaseList;
        this.useCaseAssociationList = useCaseAssociationList;
    }

    @Override
    public UMLElement getElementByJSONID(String jsonElementId) {

        for (UMLUseCase useCase : useCaseList) {
            if (useCase.getJSONElementID().equals(jsonElementId)) {
                return useCase;
            }
        }

        // TODO

        for (UMLUseCaseAssociation association : useCaseAssociationList) {
            if (association.getJSONElementID().equals(jsonElementId)) {
                return association;
            }
        }

        return null;
    }

    public List<UMLUseCase> getUseCaseList() {
        return useCaseList;
    }

    public List<UMLUseCaseAssociation> getUseCaseAssociationList() {
        return useCaseAssociationList;
    }

    @Override
    protected List<UMLElement> getModelElements() {
        List<UMLElement> modelElements = new ArrayList<>();
        modelElements.addAll(useCaseList);
        modelElements.addAll(useCaseAssociationList);
        return modelElements;
    }
}
