package de.tum.in.www1.artemis.service.util.structureoraclegenerator;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.codehaus.jackson.map.ObjectMapper;
import org.json.JSONArray;
import org.json.JSONObject;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtEnum;
import spoon.reflect.declaration.CtInterface;
import spoon.reflect.declaration.CtType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

            String typesDiffJSON = "";
            try {
                typesDiffJSON = new ObjectMapper().writeValueAsString(typesDiff);
            } catch (IOException e) {
                log.error("Could not create the JSON for type: " + typeName + "'.", e);
            }

            if (solutionType.isEnum()) {
                CtEnum<Enum<?>> solutionEnum = (CtEnum<Enum<?>>) solutionType;
                CtEnum<Enum<?>> templateEnum = (templateType != null) ? ((CtEnum<Enum<?>>) templateType) : null;

                EnumsDiff enumsDiff = new EnumsDiff(solutionEnum, templateEnum);
                if(enumsDiff.enumsEqual) { continue; }

                typeName = enumsDiff.name;
                try {
                    typesDiffJSON += new ObjectMapper().writeValueAsString(enumsDiff);
                } catch (IOException e) {
                    log.error("Could not create the JSON for enum: " + typeName + "'.", e);
                }
            }

            if (solutionType.isInterface()) {
                CtInterface<?> solutionInterface = (CtInterface<?>) solutionType;
                CtInterface<?> templateInterface = (templateType != null) ? ((CtInterface<?>) templateType) : null;

                InterfacesDiff interfacesDiff = new InterfacesDiff(solutionInterface, templateInterface);
                if(interfacesDiff.interfacesEqual) { continue; }

                typeName = interfacesDiff.name;
                try {
                    typesDiffJSON += new ObjectMapper().writeValueAsString(interfacesDiff);
                } catch (IOException e) {
                    log.error("Could not create the JSON for interface: '" + typeName + "'.", e);
                }
            }

            if (solutionType.isClass()) {
                CtClass<?> solutionClass = (CtClass<?>) solutionType;
                CtClass<?> templateClass = (templateType != null) ? ((CtClass<?>) templateType) : null;

                ClassesDiff classesDiff = new ClassesDiff(solutionClass, templateClass);
                if(classesDiff.classesEqual) { continue; }

                typeName = classesDiff.name;
                try {
                    typesDiffJSON += new ObjectMapper().writeValueAsString(classesDiff);
                } catch (IOException e) {
                    log.error("Could not create the JSON for class: '" + typeName + "'.", e);
                }
            }

            log.info("Created JSON for: " + typeName);
            structureOracleJSON += typesDiffJSON;
        }

        return structureOracleJSON;
    }
		
	private static HashMap<CtType<?>, CtType<?>> generateSolutionAndTemplateTypePairs(String solutionProjectPath, String templateProjectPath) {
		CtModel solutionModel = generateModel(solutionProjectPath);
		CtModel templateModel = generateModel(templateProjectPath);
		
		HashMap<CtType<?>, CtType<?>> solutionAndTemplateTypes = new HashMap<CtType<?>, CtType<?>>();
		
		// Types are uniquely identified by their names
		for(CtType<?> currentSolutionType : solutionModel.getAllTypes()) {
			boolean typePresentInTemplate = false;
			
			for(CtType<?> currentTemplateType : templateModel.getAllTypes()) {
				if(currentSolutionType.getSimpleName().equals(currentTemplateType.getSimpleName())) {
					typePresentInTemplate = true;
					
					// Ignore if the types are the same
					if(!currentSolutionType.equals(currentTemplateType)) {
						solutionAndTemplateTypes.put(currentSolutionType, currentTemplateType);
						break;
					}
				}
			}
			
			// Add a null template type, if it is not present
			if(!typePresentInTemplate) {
				solutionAndTemplateTypes.put(currentSolutionType, null);
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
