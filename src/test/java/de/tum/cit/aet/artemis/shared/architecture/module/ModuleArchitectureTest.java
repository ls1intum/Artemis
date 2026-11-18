package de.tum.cit.aet.artemis.shared.architecture.module;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.lang.syntax.elements.ClassesThat;
import com.tngtech.archunit.lang.syntax.elements.GivenClassesConjunction;
import com.tngtech.archunit.lang.syntax.elements.GivenMethodsConjunction;
import com.tngtech.archunit.lang.syntax.elements.MethodsThat;

public interface ModuleArchitectureTest {

    String getModulePackage();

    default String getModuleWithSubpackage() {
        return getModulePackage() + "..";
    }

    default GivenClassesConjunction classesOfThisModule() {
        return classes().that().resideInAPackage(getModuleWithSubpackage());
    }

    default ClassesThat<GivenClassesConjunction> classesOfThisModuleThat() {
        return classesOfThisModule().and();
    }

    default GivenClassesConjunction noClassesOfThisModule() {
        return noClasses().that().resideInAPackage(getModuleWithSubpackage());
    }

    default ClassesThat<GivenClassesConjunction> noClassesOfThisModuleThat() {
        return noClassesOfThisModule().and();
    }

    default GivenMethodsConjunction methodsOfThisModule() {
        return methods().that().areDeclaredInClassesThat().resideInAPackage(getModuleWithSubpackage());
    }

    default MethodsThat<? extends GivenMethodsConjunction> methodsOfThisModuleThat() {
        return methodsOfThisModule().and();
    }

    default GivenMethodsConjunction noMethodsOfThisModule() {
        return noMethods().that().areDeclaredInClassesThat().resideInAPackage(getModuleWithSubpackage());
    }

    default MethodsThat<? extends GivenMethodsConjunction> noMethodsOfThisModuleThat() {
        return noMethodsOfThisModule().and();
    }
}
