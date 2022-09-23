package de.tum.in.www1.artemis.service.compass.umlmodel.usecase;

import java.util.ArrayList;
import java.util.List;

import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

public class UMLUseCaseDiagram extends UMLDiagram {

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

        for (UMLUseCase useCase : getUseCaseList()) {
            if (useCase.getJSONElementID().equals(jsonElementId)) {
                return useCase;
            }
        }

        for (UMLSystemBoundary systemBoundary : getSystemBoundaryList()) {
            if (systemBoundary.getJSONElementID().equals(jsonElementId)) {
                return systemBoundary;
            }
        }

        for (UMLActor actor : getActorList()) {
            if (actor.getJSONElementID().equals(jsonElementId)) {
                return actor;
            }
        }

        for (UMLUseCaseAssociation association : getUseCaseAssociationList()) {
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

    public List<UMLSystemBoundary> getSystemBoundaryList() {
        return systemBoundaryList;
    }

    public List<UMLActor> getActorList() {
        return actorList;
    }

    @Override
    public List<UMLElement> getModelElements() {
        List<UMLElement> modelElements = new ArrayList<>();
        modelElements.addAll(useCaseList);
        modelElements.addAll(useCaseAssociationList);
        modelElements.addAll(systemBoundaryList);
        modelElements.addAll(actorList);
        return modelElements;
    }
}
