package de.tum.in.www1.artemis.service.hestia.structural;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.thoughtworks.qdox.model.*;

public interface StructuralElement {

    String SINGLE_INDENTATION = "    ";

    /**
     * Generates well formatted Java code for a structural element
     *
     * @param structuralClassElements The elements of the class from the test.json
     * @param solutionClass           The class read by QDox
     * @return The code for the element
     */
    String getSourceCode(StructuralClassElements structuralClassElements, JavaClass solutionClass);

    /**
     * Generates the code for annotations that are required by the test.json file.
     * Annotations that are present in the source code, but not in the test.json file will be excluded.
     *
     * @param structuralAnnotations The annotation names from the test.json
     * @param annotatedElement      The annotated element (e.g. method) read by QDox
     * @return The code for the annotations
     */
    default String getAnnotationsString(List<String> structuralAnnotations, JavaAnnotatedElement annotatedElement) {
        if (annotatedElement == null) {
            return String.join("\n", structuralAnnotations) + "\n";
        }
        else {
            return annotatedElement.getAnnotations().stream()
                    .filter(solutionAnnotation -> structuralAnnotations.contains(solutionAnnotation.getType().getSimpleName())
                            || "Override".equals(solutionAnnotation.getType().getSimpleName()))
                    .map(annotation -> annotation.getCodeBlock().replace(annotation.getType().getGenericCanonicalName(), annotation.getType().getSimpleName()))
                    .collect(Collectors.joining());
        }
    }

    /**
     * Creates the String representation of a generics declaration
     *
     * @param typeParameters The generic type parameters
     * @return The String representation
     */
    default String getGenericTypesString(List<JavaTypeVariable<JavaGenericDeclaration>> typeParameters) {
        return "<" + typeParameters.stream().map(JavaType::getGenericValue).map(type -> type.substring(1, type.length() - 1)).collect(Collectors.joining(", ")) + ">";
    }

    /**
     * Formats the modifiers properly.
     * Currently, it only removes the 'optional: ' tags and joins them together.
     *
     * @param modifiers The modifiers array
     * @return The formatted modifiers
     */
    default String formatModifiers(List<String> modifiers) {
        if (modifiers == null) {
            return "";
        }
        return modifiers.stream().map(modifier -> modifier.replace("optional: ", "")).collect(Collectors.joining(" "));
    }

    /**
     * Checks if the parameters from the source files and those from the test.json match.
     * This is used for methods and constructor parameters.
     * Contains special handling for generics
     *
     * @param parameters          The parameters from the test.json file
     * @param solutionParameters  The parameters from the source code
     * @param genericDeclarations The current generic declarations
     * @return false if any parameter does not match
     */
    default boolean doParametersMatch(List<String> parameters, List<JavaParameter> solutionParameters, List<JavaTypeVariable<JavaGenericDeclaration>> genericDeclarations) {
        if (parameters == null) {
            return solutionParameters.isEmpty();
        }
        if (parameters.size() != solutionParameters.size()) {
            return false;
        }
        for (int i = 0; i < parameters.size(); i++) {
            var typeMatches = parameters.get(i).equals(solutionParameters.get(i).getType().getValue());
            var isGeneric = false;
            for (JavaTypeVariable<JavaGenericDeclaration> type : genericDeclarations) {
                if (type.getName().equals(solutionParameters.get(i).getType().getValue()) || (type.getName() + "[]").equals(solutionParameters.get(i).getType().getValue())) {
                    isGeneric = true;
                    break;
                }
            }
            if (!typeMatches && !isGeneric) {
                return false;
            }
        }
        return true;
    }

    /**
     * Generates the string representing the source code of a parameter list
     *
     * @param parameterTypes The parameters from the test.json file
     * @param javaExecutable The executable (e.g. method) read by QDox to take the parameters from
     * @return The parameter source code
     */
    default String generateParametersString(List<String> parameterTypes, JavaExecutable javaExecutable) {
        List<JavaParameter> solutionParameters = javaExecutable != null ? javaExecutable.getParameters() : Collections.emptyList();
        String result = "(";
        if (parameterTypes != null) {
            for (int i = 0; i < parameterTypes.size(); i++) {
                if (solutionParameters.size() > i) {
                    // Use original parameter names
                    parameterTypes.set(i, solutionParameters.get(i).getType().getGenericValue() + " " + solutionParameters.get(i).getName());
                }
                else {
                    // Use var[i] as a fallback
                    parameterTypes.set(i, parameterTypes.get(i) + " var" + i);
                }
            }
            result += String.join(", ", parameterTypes);
        }
        result += ")";
        return result;
    }
}
