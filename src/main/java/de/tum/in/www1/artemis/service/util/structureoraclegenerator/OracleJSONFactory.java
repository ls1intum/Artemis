package de.tum.in.www1.artemis.service.util.structureoraclegenerator;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonMethod;
import org.codehaus.jackson.map.ObjectMapper;

import spoon.Launcher;

import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtEnum;
import spoon.reflect.declaration.CtInterface;
import spoon.reflect.declaration.CtType;

import org.slf4j.Logger;

/**
 * This class serves as a factory for the oracle.
 */
public class OracleJSONFactory {

    public static String generateStructureOracleJSON(String solutionProjectPath, String templateProjectPath, Logger log) {
        String structureOracleJSON = "";
        HashMap<CtType<?>, CtType<?>> solutionAndTemplateTypes = generateSolutionAndTemplateTypePairs(solutionProjectPath, templateProjectPath);

        for (Map.Entry<CtType<?>, CtType<?>> entry : solutionAndTemplateTypes.entrySet()) {
            CtType<?> solutionType = entry.getKey();
            CtType<?> templateType = entry.getValue();

            TypesDiff typesDiff = new TypesDiff(solutionType, templateType);
            String typeName = typesDiff.name;
            String typesDiffJSON = generateTypesJSON(typesDiff, "type " + typeName, log);

            if (solutionType.isEnum()) {
                CtEnum<Enum<?>> solutionEnum = (CtEnum<Enum<?>>) solutionType;
                CtEnum<Enum<?>> templateEnum = (CtEnum<Enum<?>>) templateType;

                EnumsDiff enumsDiff = new EnumsDiff(solutionEnum, templateEnum);
                if(enumsDiff.enumsEqual) { continue; }

                typesDiffJSON += generateTypesJSON(enumsDiff, "enum " + typeName, log);
            }

            if (solutionType.isInterface()) {
                CtInterface<?> solutionInterface = (CtInterface<?>) solutionType;
                CtInterface<?> templateInterface = (CtInterface<?>) templateType;

                InterfacesDiff interfacesDiff = new InterfacesDiff(solutionInterface, templateInterface);
                if(interfacesDiff.interfacesEqual) { continue; }

                typesDiffJSON += generateTypesJSON(interfacesDiff, "interface " + typeName, log);
            }

            if (solutionType.isClass()) {
                CtClass<?> solutionClass = (CtClass<?>) solutionType;
                CtClass<?> templateClass = (CtClass<?>) templateType;

                ClassesDiff classesDiff = new ClassesDiff(solutionClass, templateClass);
                if(classesDiff.classesEqual) { continue; }

                typesDiffJSON += generateTypesJSON(classesDiff, "class", log);
            }

            structureOracleJSON += typesDiffJSON;
        }

        return structureOracleJSON;
    }

    private static String generateTypesJSON(TypesDiff typesDiff, String typeDescription, Logger log) {
        try {
            log.info("Creating JSON for: " + typeDescription + "...");
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.setVisibility(JsonMethod.FIELD, JsonAutoDetect.Visibility.ANY);
            return objectMapper.writeValueAsString(typesDiff);
        } catch (IOException e) {
            log.error("Could not create the JSON for " + typeDescription + ": '" + typesDiff.name + "'.", e);
            return "";
        }
    }
		
	private static HashMap<CtType<?>, CtType<?>> generateSolutionAndTemplateTypePairs(String solutionProjectPath, String templateProjectPath) {
		Collection<CtType<?>> solutionTypes = generateModel(solutionProjectPath).getAllTypes();
		Collection<CtType<?>> templateTypes = generateModel(templateProjectPath).getAllTypes();
		
		HashMap<CtType<?>, CtType<?>> solutionAndTemplateTypes = new HashMap<CtType<?>, CtType<?>>();

		for(CtType<?> solutionType : solutionTypes) {
		    // Put an empty template class as a default placeholder.
            solutionAndTemplateTypes.put(solutionType, null);

		    for(CtType<?> templateType : templateTypes) {
		        // If an exact same template class is found, then remove the pair and continue
		        if(solutionType.equals(templateType)) {
		            solutionAndTemplateTypes.remove(solutionType);
		            continue;
                } else if(solutionType.getSimpleName().equals(templateType.getSimpleName())) {
                    // If a template class with the same name gets found, then replace the empty template with the real one.
                    solutionAndTemplateTypes.put(solutionType, templateType);
                    break;
                }
            }
        }
		
		return solutionAndTemplateTypes;
	}
	
	private static CtModel generateModel(String projectPath) {
		Launcher launcher = new Launcher();
		launcher.addInputResource(projectPath); 
		launcher.buildModel();
		return launcher.getModel();
	}
	
}
