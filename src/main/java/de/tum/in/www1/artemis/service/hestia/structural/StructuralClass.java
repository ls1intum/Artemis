package de.tum.in.www1.artemis.service.hestia.structural;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.thoughtworks.qdox.model.JavaClass;

/**
 * Element of the test.json file representing the properties of a class
 * Used for the generation of solution entries for structural test cases
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class StructuralClass implements StructuralElement {

    @JsonProperty(required = true)
    private String name;

    @JsonProperty(value = "package", required = true)
    private String packageName;

    private String superclass;

    private List<String> modifiers = new ArrayList<>();

    @JsonProperty(defaultValue = "false")
    private boolean isInterface;

    @JsonProperty(defaultValue = "false")
    private boolean isEnum;

    private List<String> interfaces = new ArrayList<>();

    private List<String> annotations = new ArrayList<>();

    @Override
    public String getSourceCode(StructuralClassElements structuralClassElements, JavaClass solutionClass) {
        String classSolutionCode = "package " + this.getPackageName() + ";\n\n";

        if (!this.getAnnotations().isEmpty()) {
            classSolutionCode += getAnnotationsString(this.getAnnotations(), solutionClass);
        }
        classSolutionCode += getClassHeaderCode(solutionClass);
        // Class Body
        classSolutionCode += "{\n";
        classSolutionCode += SINGLE_INDENTATION;
        if (this.isEnum()) {
            classSolutionCode += String.join(", ", structuralClassElements.getEnumValues());
        }
        classSolutionCode += "\n}";

        return classSolutionCode;
    }

    private String getClassHeaderCode(JavaClass solutionClass) {
        String classHeaderCode = "";
        if (!this.getModifiers().isEmpty()) {
            classHeaderCode += formatModifiers(this.getModifiers()) + " ";
        }
        classHeaderCode += (this.isInterface() ? "interface" : (this.isEnum() ? "enum" : "class")) + " ";
        classHeaderCode += this.getName();
        if (solutionClass != null && !solutionClass.getTypeParameters().isEmpty()) {
            classHeaderCode += getGenericTypesString(solutionClass.getTypeParameters());
        }
        classHeaderCode += " ";
        classHeaderCode += getInheritanceCode();
        return classHeaderCode;
    }

    private String getInheritanceCode() {
        String inheritanceCode = "";
        if (this.getSuperclass() != null) {
            inheritanceCode += "extends " + this.getSuperclass() + " ";
        }
        if (!this.getInterfaces().isEmpty()) {
            inheritanceCode += "implements " + String.join(", ", this.getInterfaces()) + " ";
        }
        return inheritanceCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getSuperclass() {
        return superclass;
    }

    public void setSuperclass(String superclass) {
        this.superclass = superclass;
    }

    public boolean isInterface() {
        return isInterface;
    }

    public void setIsInterface(boolean anInterface) {
        isInterface = anInterface;
    }

    public boolean isEnum() {
        return isEnum;
    }

    public void setIsEnum(boolean anEnum) {
        isEnum = anEnum;
    }

    public List<String> getInterfaces() {
        return interfaces;
    }

    public void setInterfaces(List<String> interfaces) {
        this.interfaces = interfaces;
    }

    public List<String> getModifiers() {
        return modifiers;
    }

    public void setModifiers(List<String> modifiers) {
        this.modifiers = modifiers;
    }

    public List<String> getAnnotations() {
        return annotations;
    }

    public void setAnnotations(List<String> annotations) {
        this.annotations = annotations;
    }
}
