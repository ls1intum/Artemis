package de.tum.in.www1.artemis.service.programming;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;

/**
 * The policy for choosing the appropriate service for upgrading of template files
 */
@Service
public class TemplateUpgradePolicy {

    private final JavaKotlinTemplateUpgradeService javaKotlinRepositoryUpgradeService;

    private final DefaultTemplateUpgradeService defaultRepositoryUpgradeService;

    public TemplateUpgradePolicy(JavaKotlinTemplateUpgradeService javaKotlinRepositoryUpgradeService, DefaultTemplateUpgradeService defaultRepositoryUpgradeService) {
        this.javaKotlinRepositoryUpgradeService = javaKotlinRepositoryUpgradeService;
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
            case JAVA, KOTLIN -> javaKotlinRepositoryUpgradeService;
            case PYTHON, C, HASKELL, VHDL, ASSEMBLER, SWIFT -> defaultRepositoryUpgradeService;
        };
    }
}
