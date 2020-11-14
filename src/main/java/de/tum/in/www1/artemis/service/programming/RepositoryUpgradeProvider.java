package de.tum.in.www1.artemis.service.programming;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;

@Service
public class RepositoryUpgradeProvider {

    private final JavaRepositoryUpgradeService javaRepositoryUpgradeService;

    private final DefaultRepositoryUpgradeService defaultRepositoryUpgradeService;

    public RepositoryUpgradeProvider(JavaRepositoryUpgradeService javaRepositoryUpgradeService, DefaultRepositoryUpgradeService defaultRepositoryUpgradeService) {
        this.javaRepositoryUpgradeService = javaRepositoryUpgradeService;
        this.defaultRepositoryUpgradeService = defaultRepositoryUpgradeService;
    }

    /**
     * Returns a programming language specific service which upgrades the template files in repositories.
     *
     * @param programmingLanguage The programming language of the programming exercise to be upgraded
     * @return The upgrade service for the programming language
     */
    public RepositoryUpgradeService getUpgradeService(ProgrammingLanguage programmingLanguage) {
        return switch (programmingLanguage) {
            case JAVA, KOTLIN -> javaRepositoryUpgradeService;
            case PYTHON, C, HASKELL, VHDL, ASSEMBLER, SWIFT -> defaultRepositoryUpgradeService;
        };
    }
}
