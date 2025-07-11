package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;

/**
 * The policy for choosing the appropriate service for upgrading of template files
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class TemplateUpgradePolicyService {

    private final JavaTemplateUpgradeService javaRepositoryUpgradeService;

    private final DefaultTemplateUpgradeService defaultRepositoryUpgradeService;

    public TemplateUpgradePolicyService(JavaTemplateUpgradeService javaRepositoryUpgradeService, DefaultTemplateUpgradeService defaultRepositoryUpgradeService) {
        this.javaRepositoryUpgradeService = javaRepositoryUpgradeService;
        this.defaultRepositoryUpgradeService = defaultRepositoryUpgradeService;
    }

    /**
     * Returns a programming language specific service which upgrades the template files in repositories.
     *
     * @param programmingLanguage The programming language of the programming exercise to be upgraded
     * @return The upgrade service for the programming language
     */
    public TemplateUpgradeService getUpgradeService(ProgrammingLanguage programmingLanguage) {
        return switch (programmingLanguage) {
            case JAVA -> javaRepositoryUpgradeService;
            case KOTLIN, PYTHON, C, HASKELL, VHDL, ASSEMBLER, SWIFT, OCAML, EMPTY, RUST, JAVASCRIPT, R, C_PLUS_PLUS, TYPESCRIPT, C_SHARP, GO, BASH, MATLAB, RUBY, DART ->
                defaultRepositoryUpgradeService;
            case SQL, POWERSHELL, ADA, PHP -> throw new UnsupportedOperationException("Unsupported programming language: " + programmingLanguage);
        };
    }
}
