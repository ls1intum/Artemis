"""Exercise server test selection against real, isolated Git histories.

Run: python3 -m unittest discover -s supporting_scripts/tests -p 'test_changed_modules.py'
"""

from pathlib import Path
import subprocess
import tempfile
import unittest


SELECTOR = Path(__file__).resolve().parents[1] / "get_changed_modules.sh"
PACKAGE = "de.tum.cit.aet.artemis"
MAIN = "src/main/java/de/tum/cit/aet/artemis"
TEST = "src/test/java/de/tum/cit/aet/artemis"


class ChangedModulesTest(unittest.TestCase):
    def setUp(self):
        self.workspace = tempfile.TemporaryDirectory(prefix="artemis-server-selection-")
        self.addCleanup(self.workspace.cleanup)
        self.root = Path(self.workspace.name)
        self.git("init", "-q", "-b", "base")
        self.git("config", "user.email", "selector@example.invalid")
        self.git("config", "user.name", "Server selector test")
        self.java(MAIN, "alpha/service/AlphaService.java")
        self.java(MAIN, "beta/service/BetaService.java", imports=["alpha.service.AlphaService"])
        self.java(MAIN, "gamma/web/GammaResource.java", imports=["beta.service.BetaService"])
        self.java(MAIN, "delta/service/DeltaService.java")
        for module in ("epsilon", "zeta", "theta", "kappa", "omega"):
            self.java(MAIN, f"{module}/service/{module.title()}Service.java")
        for module in ("alpha", "beta", "gamma", "delta", "epsilon", "zeta", "theta", "kappa", "omega"):
            self.java(TEST, f"{module}/{module.title()}Test.java")
        self.commit()
        self.git("switch", "-q", "-c", "feature")

    def git(self, *args):
        return subprocess.run(
            ["git", *args], cwd=self.root, capture_output=True, text=True, check=True
        ).stdout.strip()

    def write(self, path, contents):
        destination = self.root / path
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(contents)

    def java(self, source_root, relative, imports=(), declaration=None, body=""):
        path = Path(relative)
        package = PACKAGE + "." + ".".join(path.parent.parts)
        imported = "".join(f"import {PACKAGE}.{value};\n" for value in imports)
        self.write(
            f"{source_root}/{relative}",
            f"package {package};\n{imported}"
            f"{declaration or 'public class ' + path.stem} {{ {body} }}\n",
        )

    def commit(self):
        self.git("add", "--all")
        self.git("commit", "-qm", "Fixture change")

    def change(self, path):
        file = self.root / path
        file.write_text(file.read_text() + "\n// changed\n")

    def selected(self, base="base"):
        result = subprocess.run(
            ["bash", str(SELECTOR), base], cwd=self.root, capture_output=True, text=True
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        return result.stdout.strip()

    def test_leaf_service_selects_own_module(self):
        self.change(f"{MAIN}/delta/service/DeltaService.java")
        self.assertEqual(self.selected(), "delta")

    def test_service_selects_transitive_consumers(self):
        self.change(f"{MAIN}/alpha/service/AlphaService.java")
        self.assertEqual(self.selected(), "alpha,beta,gamma")

    def test_local_test_change_does_not_follow_production_dependencies(self):
        self.change(f"{TEST}/alpha/AlphaTest.java")
        self.assertEqual(self.selected(), "alpha")

    def test_test_consumers_are_selected(self):
        self.java(TEST, "delta/DeltaTest.java", imports=["alpha.service.AlphaService"])
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/alpha/service/AlphaService.java")
        self.assertEqual(self.selected(), "alpha,beta,delta,gamma")

    def test_test_inheritance_does_not_treat_all_sibling_tests_as_consumers(self):
        self.java(TEST, "shared/base/AbstractTest.java", declaration="public abstract class AbstractTest")
        self.java(TEST, "alpha/AlphaTest.java", imports=["shared.base.AbstractTest", "alpha.service.AlphaService"],
                  declaration="public class AlphaTest extends AbstractTest")
        self.java(TEST, "delta/DeltaTest.java", imports=["shared.base.AbstractTest"],
                  declaration="public class DeltaTest extends AbstractTest")
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/alpha/service/AlphaService.java")
        self.assertEqual(self.selected(), "alpha,beta,gamma")

    def test_base_tip_changes_are_excluded_by_merge_base(self):
        self.change(f"{TEST}/alpha/AlphaTest.java")
        self.commit()
        self.git("switch", "-q", "base")
        self.change(f"{TEST}/delta/DeltaTest.java")
        self.commit()
        self.git("switch", "-q", "feature")
        self.assertEqual(self.selected(), "alpha")

    def test_service_deletion_retains_old_consumers(self):
        (self.root / f"{MAIN}/alpha/service/AlphaService.java").unlink()
        self.assertEqual(self.selected(), "alpha,beta,gamma")

    def test_removed_import_retains_base_snapshot_consumers(self):
        self.java(MAIN, "beta/service/BetaService.java")
        self.change(f"{MAIN}/alpha/service/AlphaService.java")
        self.assertEqual(self.selected(), "alpha,beta,gamma")

    def test_cross_module_rename_selects_both_owners_and_old_consumers(self):
        self.git("mv", f"{MAIN}/alpha/service/AlphaService.java", f"{MAIN}/delta/service/AlphaService.java")
        self.java(MAIN, "delta/service/AlphaService.java")
        self.assertEqual(self.selected(), "alpha,beta,delta,gamma")

    def test_interface_consumers_are_selected_for_implementation_changes(self):
        self.java(MAIN, "alpha/api/Feature.java", declaration="public interface Feature")
        self.java(MAIN, "alpha/service/AlphaService.java", imports=["alpha.api.Feature"],
                  declaration="public class AlphaService implements Feature")
        self.java(MAIN, "beta/service/BetaService.java", imports=["alpha.api.Feature"])
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/alpha/service/AlphaService.java")
        self.assertEqual(self.selected(), "alpha,beta,gamma")

    def test_supertype_consumers_are_selected_for_subclass_changes(self):
        self.java(MAIN, "alpha/service/Parent.java")
        self.java(MAIN, "alpha/service/AlphaService.java", declaration="public class AlphaService extends Parent")
        self.java(MAIN, "beta/service/BetaService.java", imports=["alpha.service.Parent"])
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/alpha/service/AlphaService.java")
        self.assertEqual(self.selected(), "alpha,beta,gamma")

    def test_nested_interface_wildcard_import_retains_interface_consumers(self):
        self.java(MAIN, "alpha/api/Contract.java", body="public interface Feature {}")
        self.java(MAIN, "alpha/service/AlphaService.java", imports=["alpha.api.Contract.*"],
                  declaration="public class AlphaService implements Feature")
        self.java(MAIN, "beta/service/BetaService.java", imports=["alpha.api.Contract.Feature"])
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/alpha/service/AlphaService.java")
        self.assertEqual(self.selected(), "alpha,beta,gamma")

    def test_external_supertype_wiring_cannot_be_scoped_by_internal_imports(self):
        path = self.root / f"{MAIN}/alpha/service/AlphaService.java"
        path.write_text(
            f"package {PACKAGE}.alpha.service;\n"
            "import java.util.function.Supplier;\n"
            "public class AlphaService implements Supplier<String> {}\n"
        )
        self.assertEqual(self.selected(), "")

    def test_same_package_static_wildcard_and_qualified_consumers(self):
        self.java(MAIN, "alpha/service/Peer.java", body="AlphaService value;")
        self.java(MAIN, "beta/service/BetaService.java", imports=["alpha.service.*"])
        self.java(MAIN, "gamma/web/GammaResource.java", body=f"{PACKAGE}.alpha.service.Peer value;")
        self.java(MAIN, "delta/service/DeltaService.java", imports=["alpha.service.AlphaService.VALUE"])
        path = self.root / f"{MAIN}/delta/service/DeltaService.java"
        path.write_text(path.read_text().replace("import ", "import static "))
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/alpha/service/AlphaService.java")
        self.assertEqual(self.selected(), "alpha,beta,delta,gamma")

    def test_global_changes_override_feature_selection(self):
        for path in (
            "build.gradle", "gradle/test.gradle", "gradle.properties", "settings.gradle",
            "src/main/resources/config/application.yml", "src/test/resources/test.json",
            f"{MAIN}/core/service/CoreService.java", f"{TEST}/shared/base/AbstractTest.java",
            "supporting_scripts/unknown.sh", "src/main/java/other/Unknown.java",
            "packages/unknown/Config.java",
        ):
            with self.subTest(path=path):
                self.write(path, "global change")
                self.git("add", path)
                self.change(f"{MAIN}/delta/service/DeltaService.java")
                self.assertEqual(self.selected(), "")
                self.git("reset", "-q", "--", path)
                (self.root / path).unlink()

    def test_contract_changes_override_feature_selection(self):
        for directory in ("domain", "dto", "api", "config", "event", "aspects", "annotation"):
            with self.subTest(directory=directory):
                path = f"{MAIN}/alpha/{directory}/Contract.java"
                self.java(MAIN, f"alpha/{directory}/Contract.java")
                self.git("add", path)
                self.change(f"{MAIN}/delta/service/DeltaService.java")
                self.assertEqual(self.selected(), "")
                self.git("reset", "-q", "--", path)
                (self.root / path).unlink()

    def test_test_support_changes_override_feature_selection(self):
        for relative in ("alpha/util/Fixture.java", "alpha/AbstractAlphaTest.java", "alpha/FixtureFactory.java"):
            with self.subTest(relative=relative):
                self.java(TEST, relative)
                self.git("add", f"{TEST}/{relative}")
                self.change(f"{MAIN}/delta/service/DeltaService.java")
                self.assertEqual(self.selected(), "")
                self.git("reset", "-q", "--", f"{TEST}/{relative}")
                (self.root / TEST / relative).unlink()

    def test_configuration_nested_in_a_test_is_treated_as_test_support(self):
        self.java(TEST, "alpha/AlphaTest.java", body="@TestConfiguration static class Config {}")
        self.assertEqual(self.selected(), "")

    def test_dynamic_wiring_falls_back_to_full(self):
        for body in ("@EventListener void handle() {}", "void go() { publisher.publishEvent(event); }",
                     "@Scheduled void run() {}", "@Bean Object make() { return null; }",
                     "void go() { Class.forName(name); }"):
            with self.subTest(body=body):
                self.java(MAIN, "delta/service/DeltaService.java", body=body)
                self.assertEqual(self.selected(), "")

    def test_dynamic_consumer_falls_back_to_full(self):
        self.java(MAIN, "beta/service/BetaService.java", imports=["alpha.service.AlphaService"],
                  body="@EventListener void handle() {}")
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/alpha/service/AlphaService.java")
        self.assertEqual(self.selected(), "")

    def test_new_global_advice_overrides_narrow_service_selection(self):
        for annotation in ("ControllerAdvice", "RestControllerAdvice"):
            with self.subTest(annotation=annotation):
                self.java(MAIN, "delta/web/GlobalErrorHandler.java",
                          declaration=f"@{annotation} public class GlobalErrorHandler",
                          body="@ExceptionHandler(RuntimeException.class) void handle() {}")
                self.git("add", "--all")
                self.change(f"{MAIN}/alpha/service/AlphaService.java")
                self.assertEqual(self.selected(), "")

    def test_existing_global_advice_change_runs_full_suite(self):
        self.java(MAIN, "delta/web/GlobalErrorHandler.java",
                  declaration="@RestControllerAdvice public class GlobalErrorHandler",
                  body="@ExceptionHandler(RuntimeException.class) void handle() {}")
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/delta/web/GlobalErrorHandler.java")
        self.assertEqual(self.selected(), "")

    def test_global_web_meta_configuration_overrides_narrow_service_selection(self):
        for annotation in ("EnableWebMvc", "EnableScheduling", "EnableCustomFeature",
                           'ImportResource("classpath:global.xml")'):
            with self.subTest(annotation=annotation):
                self.java(MAIN, "delta/web/WebFeatures.java",
                          declaration=f"@{annotation} public class WebFeatures")
                self.git("add", "--all")
                self.change(f"{MAIN}/alpha/service/AlphaService.java")
                self.assertEqual(self.selected(), "")

    def test_documentation_and_client_changes_do_not_override_service_selection(self):
        self.write("documentation/docs/feature.md", "Documentation")
        self.write("src/main/webapp/app/example.ts", "// client")
        self.write("packages/tum-aet-ui/src/example.ts", "// client library")
        self.git("add", "--all")
        self.change(f"{MAIN}/delta/service/DeltaService.java")
        self.assertEqual(self.selected(), "delta")

    def test_translation_disk_input_overrides_narrow_service_selection(self):
        self.write("src/main/webapp/i18n/en/global.json", '{"changed": true}')
        self.git("add", "--all")
        self.change(f"{MAIN}/delta/service/DeltaService.java")
        self.assertEqual(self.selected(), "")

    def test_feature_catalogue_disk_input_overrides_narrow_service_selection(self):
        self.write("documentation/docs/developer/feature-usage-catalogue.mdx", "Changed catalogue")
        self.git("add", "--all")
        self.change(f"{MAIN}/delta/service/DeltaService.java")
        self.assertEqual(self.selected(), "")

    def test_missing_merge_base_falls_back_to_full(self):
        self.change(f"{MAIN}/delta/service/DeltaService.java")
        self.assertEqual(self.selected("missing-ref"), "")

    def test_unparseable_changed_source_falls_back_to_full(self):
        self.write(f"{MAIN}/delta/service/DeltaService.java", "not Java")
        self.assertEqual(self.selected(), "")

    def test_generated_jpa_metamodel_imports_keep_source_consumers(self):
        self.java(MAIN, "alpha/domain/Entity.java")
        self.java(MAIN, "alpha/service/AlphaService.java", imports=["alpha.domain.Entity"],
                  declaration="public class AlphaService extends Entity")
        self.java(MAIN, "beta/service/BetaService.java", imports=["alpha.domain.Entity_"])
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/alpha/service/AlphaService.java")
        self.assertEqual(self.selected(), "alpha,beta,gamma")

    def test_package_path_mismatch_falls_back_to_full(self):
        self.java(MAIN, "delta/service/DeltaService.java")
        path = self.root / f"{MAIN}/delta/service/DeltaService.java"
        path.write_text(path.read_text().replace("artemis.delta.service", "artemis.alpha.service"))
        self.assertEqual(self.selected(), "")

    def test_unresolved_internal_import_falls_back_to_full(self):
        self.java(MAIN, "delta/service/DeltaService.java", imports=["alpha.service.Missing"])
        self.assertEqual(self.selected(), "")

    def test_new_or_unknown_module_falls_back_to_full(self):
        self.java(MAIN, "unknown/service/UnknownService.java")
        self.git("add", "--all")
        self.assertEqual(self.selected(), "")

    def test_broad_module_selection_uses_full_sharded_suite(self):
        self.java(MAIN, "epsilon/service/EpsilonService.java", imports=["gamma.web.GammaResource"])
        self.java(MAIN, "zeta/service/ZetaService.java", imports=["epsilon.service.EpsilonService"])
        # Keep the test footprint small enough that only module breadth triggers.
        for number in range(15):
            self.java(TEST, f"delta/Unrelated{number}Test.java")
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/alpha/service/AlphaService.java")
        self.assertEqual(self.selected(), "")

    def test_broad_test_footprint_uses_full_sharded_suite(self):
        # Three of nine modules, but nine of fifteen test classes are selected.
        for number in range(6):
            self.java(TEST, f"alpha/Additional{number}Test.java")
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/alpha/service/AlphaService.java")
        self.assertEqual(self.selected(), "")

    def test_global_scanners_count_toward_the_selected_test_footprint(self):
        # A leaf controller has no import edge to runtime/global scanners.
        self.java(MAIN, "delta/web/PlantUmlResource.java")
        for number in range(8):
            self.java(TEST, f"core/GlobalScanner{number}Test.java")
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/delta/web/PlantUmlResource.java")
        self.assertEqual(self.selected(), "")

    def test_no_changes_falls_back_to_full(self):
        self.assertEqual(self.selected(), "")


if __name__ == "__main__":
    unittest.main(verbosity=2)
