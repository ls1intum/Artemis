package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Pins the single source of truth that the differential oracle (exemption) and persistence (zero-weighting) both consult, so the two call sites cannot drift:
 * build/compile/configure
 * gates are recognised across the real harness name forms, while behaviour tests — including ones whose name merely starts with a gate word — are never misclassified.
 */
class BuildGateTestNamesTest {

    @ParameterizedTest(name = "\"{0}\" is a build gate")
    @ValueSource(strings = { "compile", "Compile", "testCompile", "TestCompile", "configure", "TestConfigure", "build", "TestBuild", "cmake", "CompileSort", "ConfigureDebug",
            "BuildTests", "GBS-Tester-1.36.CompileSort", "GBS-Tester-1.36.TestConfigure", "structural.Compile", "TestConfigure()" })
    void recognisesBuildGates(String name) {
        assertThat(BuildGateTestNames.isBuildGate(name)).as(name).isTrue();
    }

    @ParameterizedTest(name = "\"{0}\" is NOT a build gate")
    @ValueSource(strings = { "sort-test.stack_empty_initially", "TestCatch2", "testPushPop", "compiles_an_empty_program", "buildsTheList", "configures_nothing", "push_then_pop",
            "sort-test.size_tracks_elements", "GBS-Tester-1.36.TestSortAscending" })
    void rejectsBehaviourTests(String name) {
        assertThat(BuildGateTestNames.isBuildGate(name)).as(name).isFalse();
    }

    @Test
    void nullIsNotABuildGate() {
        assertThat(BuildGateTestNames.isBuildGate(null)).isFalse();
    }
}
