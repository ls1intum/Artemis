package de.tum.cit.aet.artemis.shared.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Keeps every connection to a language model in the LLM core ({@code core.service.llm} and {@code core.config.llm}).
 * <p>
 * Models, endpoints and credentials are configured once, in the catalog under {@code artemis.llm}. A module names the preset it uses in an
 * {@code llm-preset} key, registers that key as {@code LlmPresetAssignments} so the startup validation checks it, and gets its client from
 * {@code LlmClientService}. A module that builds its own client bypasses the timeouts, retries, custom headers, Microsoft Foundry support, cost
 * tracking and observations of the core, and needs its own keys for the URL and the API key, which the deployments then have to configure twice.
 * <p>
 * There are no exceptions. A module that needs a kind of call the core does not offer yet adds it to {@code LlmClientService}. Modules may
 * still use the request options, the response and usage types and the exception types of the SDKs. These rules cannot see a module that
 * reads its own URL and key and calls a model with a plain HTTP client; the server non-negotiables in {@code AGENTS.md} forbid that too.
 */
class LlmClientArchitectureTest extends AbstractArchitectureTest {

    private static final String[] LLM_CORE_PACKAGES = { ARTEMIS_PACKAGE + ".core.service.llm..", ARTEMIS_PACKAGE + ".core.config.llm.." };

    private static final DescribedPredicate<JavaClass> PROVIDER_CLIENTS = resideInAnyPackage("com.openai.client..", "com.openai.services..",
            "org.springframework.ai.openai.setup..").or(nameMatching("org\\.springframework\\.ai\\.openai\\.(responses\\.)?OpenAi\\w+Model(\\$Builder)?"))
            .or(nameMatching("org\\.springframework\\.ai\\.openai\\.http\\.okhttp\\.SpringAiOpenAiHttpClient(\\$.*)?")).as("are clients or models of an LLM provider");

    private static final DescribedPredicate<JavaClass> LLM_ENDPOINTS = nameMatching("de\\.tum\\.cit\\.aet\\.artemis\\.core\\.config\\.llm\\.LlmProperties\\$Endpoint.*")
            .<JavaClass>forSubtype().as("are LLM endpoints, which hold URLs and keys");

    private static final DescribedPredicate<JavaMethodCall> ENDPOINT_ACCESS = new DescribedPredicate<>("methods that return LLM endpoints") {

        @Override
        public boolean test(JavaMethodCall call) {
            return call.getTargetOwner().getName().equals(ARTEMIS_PACKAGE + ".core.config.llm.LlmProperties") && call.getName().matches("endpoints|endpointOf|requiredEndpointOf");
        }
    };

    @Test
    void onlyTheLlmCoreBuildsClientsOfAnLlmProvider() {
        ArchRule rule = noClasses().that().resideOutsideOfPackages(LLM_CORE_PACKAGES).should().dependOnClassesThat(PROVIDER_CLIENTS)
                .because("every LLM call uses a preset of artemis.llm through LlmClientService; add a missing kind of call there.");
        rule.check(productionClasses);
    }

    @Test
    void onlyTheLlmCoreReadsTheLlmEndpoints() {
        ArchRule rule = noClasses().that().resideOutsideOfPackages(LLM_CORE_PACKAGES).should().dependOnClassesThat(LLM_ENDPOINTS).orShould().callMethodWhere(ENDPOINT_ACCESS)
                .because("a module names a preset and gets its client from LlmClientService; it never sees an endpoint's URL or key.");
        rule.check(productionClasses);
    }
}
