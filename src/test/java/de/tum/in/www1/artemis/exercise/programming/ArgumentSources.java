package de.tum.in.www1.artemis.exercise.programming;

import static de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage.ASSEMBLER;
import static de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage.OCAML;
import static de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage.VHDL;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;

public class ArgumentSources {

    // TODO: Add template for VHDL, Assembler, and Ocaml and activate those languages here again
    public static Set<ProgrammingLanguage> generateJenkinsSupportedLanguages() {
        List<ProgrammingLanguage> unsupportedLanguages = List.of(VHDL, ASSEMBLER, OCAML);

        var supportedLanguages = EnumSet.copyOf(ProgrammingLanguage.getEnabledLanguages());
        unsupportedLanguages.forEach(supportedLanguages::remove);
        return supportedLanguages;
    }
}
