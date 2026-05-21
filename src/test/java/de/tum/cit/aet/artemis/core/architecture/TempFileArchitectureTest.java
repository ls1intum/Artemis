package de.tum.cit.aet.artemis.core.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.nio.file.Files;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchRule;

import de.tum.cit.aet.artemis.shared.architecture.AbstractArchitectureTest;

/**
 * Architecture tests for temporary file/directory creation.
 * <p>
 * All code must use {@code TempFileUtilService} instead of directly calling
 * {@link Files#createTempDirectory} or {@link Files#createTempFile}.
 * <p>
 * This ensures proper isolation and cleanup of temporary files within the
 * configured temp directory ({@code artemis.temp-path}).
 *
 * @see de.tum.cit.aet.artemis.core.service.TempFileUtilService
 */
class TempFileArchitectureTest extends AbstractArchitectureTest {

    /**
     * Predicate that matches ANY call to Files.createTempDirectory.
     */
    private static DescribedPredicate<JavaMethodCall> callsCreateTempDirectory() {
        return new DescribedPredicate<>("calls Files.createTempDirectory") {

            @Override
            public boolean test(JavaMethodCall call) {
                String ownerName = call.getTargetOwner().getFullName();
                String methodName = call.getTarget().getName();
                return ownerName.equals(Files.class.getName()) && methodName.equals("createTempDirectory");
            }
        };
    }

    /**
     * Predicate that matches ANY call to Files.createTempFile.
     */
    private static DescribedPredicate<JavaMethodCall> callsCreateTempFile() {
        return new DescribedPredicate<>("calls Files.createTempFile") {

            @Override
            public boolean test(JavaMethodCall call) {
                String ownerName = call.getTargetOwner().getFullName();
                String methodName = call.getTarget().getName();
                return ownerName.equals(Files.class.getName()) && methodName.equals("createTempFile");
            }
        };
    }

    @Test
    void testNoDirectCreateTempDirectory() {
        // Only TempFileUtilService is allowed to use Files.createTempDirectory
        var classesToCheck = allClasses.that(not(simpleName("TempFileUtilService")));

        ArchRule rule = noClasses().should().callMethodWhere(callsCreateTempDirectory())
                .because("All code must use TempFileUtilService.createTempDirectory() instead of Files.createTempDirectory(). "
                        + "This ensures proper temp file isolation within the configured temp path. "
                        + "Inject TempFileUtilService and call createTempDirectory(prefix) or createTempDirectory(parent, prefix).");

        rule.check(classesToCheck);
    }

    @Test
    void testNoDirectCreateTempFile() {
        // Only TempFileUtilService is allowed to use Files.createTempFile
        var classesToCheck = allClasses.that(not(simpleName("TempFileUtilService")));

        ArchRule rule = noClasses().should().callMethodWhere(callsCreateTempFile())
                .because("All code must use TempFileUtilService.createTempFile() instead of Files.createTempFile(). "
                        + "This ensures proper temp file isolation within the configured temp path. "
                        + "Inject TempFileUtilService and call createTempFile(prefix, suffix) or createTempFile(parent, prefix, suffix).");

        rule.check(classesToCheck);
    }
}
