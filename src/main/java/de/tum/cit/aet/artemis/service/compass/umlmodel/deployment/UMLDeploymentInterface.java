package de.tum.cit.aet.artemis.service.compass.umlmodel.deployment;

import de.tum.cit.aet.artemis.service.compass.umlmodel.component.UMLComponentInterface;

public class UMLDeploymentInterface extends UMLComponentInterface {

    public static final String UML_DEPLOYMENT_INTERFACE_TYPE = "DeploymentInterface";

    public UMLDeploymentInterface(String name, String jsonElementID) {
        super(name, jsonElementID);
    }

    @Override
    public String getType() {
        return UML_DEPLOYMENT_INTERFACE_TYPE;
    }

    @Override
    public String toString() {
        return "Deployment Interface " + getName();
    }
}
