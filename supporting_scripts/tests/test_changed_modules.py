"""Exercise server test selection against real, isolated Git histories.

Run: python3 -m unittest discover -s supporting_scripts/tests -p 'test_changed_modules.py'
"""

from pathlib import Path
import json
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
        # Fixtures model real type uses, rather than unused/Javadoc-only imports.
        body += " " + " ".join(f"{value.rsplit('.', 1)[1]} reference{index};"
                                for index, value in enumerate(imports)
                                if not value.endswith(".*") and value.rsplit('.', 1)[1][0].isupper())
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

    def plan(self, base="base"):
        result = subprocess.run(
            ["python3", str(SELECTOR.with_name("select_server_test_modules.py")), "--format", "json", base],
            cwd=self.root, capture_output=True, text=True,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        return json.loads(result.stdout)

    def test_class_plan_selects_changed_test_without_other_tests_in_its_module(self):
        self.java(TEST, "delta/UnrelatedTest.java")
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{TEST}/delta/DeltaTest.java")
        plan = self.plan()
        self.assertEqual(plan["mode"], "selected", plan)
        self.assertEqual(plan["tests"], [PACKAGE + ".delta.DeltaTest"])
        self.assertEqual(plan["selectedTestClasses"], 1)
        self.assertEqual(plan["totalTestClasses"], 10)

    def test_class_plan_keeps_global_scanners_without_all_core_unit_tests(self):
        self.java(TEST, "core/authorization/EndpointTest.java", body="RequestMappingHandlerMapping mappings;")
        self.java(TEST, "core/service/UnrelatedTest.java")
        self.java(TEST, "shared/architecture/AbstractArchitectureTest.java", declaration="public abstract class AbstractArchitectureTest")
        self.java(TEST, "epsilon/architecture/EpsilonArchitectureTest.java")
        self.java(TEST, "videosource/service/GocastLoggingSafetyTest.java")
        self.java(TEST, "core/service/SourceReaderTest.java", body='String input = "src/main/java";')
        self.java(MAIN, "delta/web/DeltaResource.java")
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/delta/web/DeltaResource.java")
        plan = self.plan()
        self.assertEqual(plan["mode"], "selected", plan)
        for name in ("delta.DeltaTest", "core.authorization.EndpointTest", "epsilon.architecture.EpsilonArchitectureTest",
                     "videosource.service.GocastLoggingSafetyTest", "core.service.SourceReaderTest"):
            self.assertIn(PACKAGE + "." + name, plan["tests"])
        self.assertNotIn(PACKAGE + ".core.service.UnrelatedTest", plan["tests"])

    def test_class_plan_unchanged_conditional_does_not_require_full_suite(self):
        self.java(MAIN, "delta/service/DeltaService.java", declaration="@Conditional(DeltaEnabled.class) public class DeltaService")
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/delta/service/DeltaService.java")
        self.assertEqual(self.plan()["mode"], "selected")
        self.java(MAIN, "delta/service/DeltaService.java", declaration="@Conditional(OtherEnabled.class) public class DeltaService")
        self.assertEqual(self.plan()["mode"], "full")

    def test_class_plan_conditional_does_not_hide_dynamic_behavior_on_same_line(self):
        self.java(MAIN, "delta/service/DeltaService.java", declaration="@Conditional(DeltaEnabled.class) public class DeltaService",
                  body="void send() { publisher.publishEvent(event); }")
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/delta/service/DeltaService.java")
        self.assertEqual(self.plan()["mode"], "full")

    def test_class_plan_event_listener_parameter_changes_are_wiring_changes(self):
        self.java(MAIN, "delta/service/DeltaService.java", body="@EventListener void handle(OldEvent event) {}")
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.java(MAIN, "delta/service/DeltaService.java", body="@EventListener void handle(NewEvent event) {}")
        self.assertEqual(self.plan()["mode"], "full")

    def test_class_plan_transitive_conditional_consumer_keeps_its_tests(self):
        self.java(MAIN, "beta/service/BetaService.java", imports=["alpha.service.AlphaService"],
                  declaration="@Conditional(BetaEnabled.class) public class BetaService")
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/alpha/service/AlphaService.java")
        plan = self.plan()
        self.assertEqual(plan["mode"], "selected", plan)
        self.assertIn(PACKAGE + ".beta.BetaTest", plan["tests"])
        self.assertIn(PACKAGE + ".gamma.GammaTest", plan["tests"])

    def test_class_plan_generic_argument_does_not_become_changed_parent(self):
        self.java(MAIN, "delta/service/Parent.java", declaration="public class Parent<T>")
        self.java(MAIN, "delta/service/DeltaService.java", imports=["alpha.service.AlphaService"],
                  declaration="public class DeltaService extends Parent<AlphaService>")
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/delta/service/DeltaService.java")
        plan = self.plan()
        self.assertEqual(plan["mode"], "selected", plan)
        self.assertNotIn(PACKAGE + ".alpha.AlphaTest", plan["tests"])

    def test_class_plan_marker_inheritance_does_not_change_sibling_implementations(self):
        self.java(MAIN, "delta/service/Marker.java", declaration="public interface Marker")
        self.java(MAIN, "delta/service/DeltaService.java", declaration="public class DeltaService implements Marker")
        self.java(MAIN, "alpha/service/AlphaService.java", imports=["delta.service.Marker"],
                  declaration="public class AlphaService implements Marker")
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/delta/service/DeltaService.java")
        self.assertEqual(self.plan()["modules"], ["delta"])

    def test_class_plan_empty_intermediate_superclass_keeps_inherited_behavior_consumers(self):
        self.java(MAIN, "delta/service/Behavior.java", declaration="public interface Behavior", body="void execute();")
        self.java(MAIN, "delta/service/Intermediate.java", declaration="public abstract class Intermediate implements Behavior")
        self.java(MAIN, "delta/service/DeltaService.java", declaration="public class DeltaService extends Intermediate")
        self.java(MAIN, "beta/service/BetaService.java", imports=["delta.service.Intermediate"])
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/delta/service/DeltaService.java")
        self.assertIn("beta", self.plan()["modules"])

    def test_class_plan_inherited_dynamic_behavior_requires_full_suite(self):
        self.java(MAIN, "delta/service/Parent.java", body="void emit() { publisher.publishEvent(event); }")
        self.java(MAIN, "delta/service/DeltaService.java", declaration="public class DeltaService extends Parent")
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/delta/service/DeltaService.java")
        self.assertEqual(self.plan()["mode"], "full")

    def test_class_plan_inherited_external_spi_requires_full_suite(self):
        self.write(f"{MAIN}/delta/service/Parent.java", f"package {PACKAGE}.delta.service;\n"
                   "import java.util.function.Supplier;\npublic abstract class Parent implements Supplier<String> {}")
        self.java(MAIN, "delta/service/DeltaService.java", declaration="public class DeltaService extends Parent")
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/delta/service/DeltaService.java")
        self.assertEqual(self.plan()["mode"], "full")

    def test_class_plan_retains_interface_dispatch_and_transitive_consumers(self):
        self.java(MAIN, "alpha/api/Feature.java", declaration="public interface Feature", body="void execute();")
        self.java(MAIN, "alpha/service/AlphaService.java", imports=["alpha.api.Feature"],
                  declaration="public class AlphaService implements Feature")
        self.java(MAIN, "beta/service/BetaService.java", imports=["alpha.api.Feature"])
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/alpha/service/AlphaService.java")
        self.assertEqual(self.plan()["modules"], ["alpha", "beta", "gamma"])

    def test_class_plan_unchanged_static_constant_does_not_propagate_method_body_change(self):
        self.java(MAIN, "delta/web/DeltaResource.java", body='public static final String ENTITY_NAME = "delta";')
        self.write(f"{MAIN}/alpha/service/AlphaService.java",
                   f"package {PACKAGE}.alpha.service;\nimport {PACKAGE}.delta.web.DeltaResource;\n"
                   'public class AlphaService { String entity = DeltaResource.ENTITY_NAME; }')
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/delta/web/DeltaResource.java")
        self.assertEqual(self.plan()["modules"], ["delta"])
        path = self.root / f"{MAIN}/delta/web/DeltaResource.java"
        path.write_text(path.read_text().replace('"delta"', '"changed"'))
        self.assertIn("alpha", self.plan()["modules"])

    def test_class_plan_mutable_static_final_field_keeps_consumers(self):
        self.java(MAIN, "delta/web/DeltaResource.java", body="public static final DeltaResource INSTANCE = new DeltaResource();")
        self.write(f"{MAIN}/alpha/service/AlphaService.java",
                   f"package {PACKAGE}.alpha.service;\nimport {PACKAGE}.delta.web.DeltaResource;\n"
                   'public class AlphaService { Object resource = DeltaResource.INSTANCE; }')
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/delta/web/DeltaResource.java")
        self.assertIn("alpha", self.plan()["modules"])

    def test_class_plan_bean_annotation_changes_require_full_suite(self):
        self.java(MAIN, "delta/service/DeltaService.java", declaration="@Service public class DeltaService")
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.java(MAIN, "delta/service/DeltaService.java", declaration="@Component public class DeltaService")
        self.assertEqual(self.plan()["mode"], "full")

    def test_class_plan_marker_membership_changes_require_full_suite(self):
        self.java(MAIN, "delta/service/Marker.java", declaration="public interface Marker")
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.java(MAIN, "delta/service/DeltaService.java", declaration="public class DeltaService implements Marker")
        self.assertEqual(self.plan()["mode"], "full")

    def test_class_plan_unused_javadoc_import_does_not_propagate_behavior(self):
        self.write(f"{MAIN}/alpha/service/AlphaService.java",
                   f"package {PACKAGE}.alpha.service;\nimport {PACKAGE}.delta.service.DeltaService;\n"
                   '/** See {@link DeltaService}. */ public class AlphaService {}')
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/delta/service/DeltaService.java")
        self.assertEqual(self.plan()["modules"], ["delta"])

    def test_class_plan_unchanged_dynamic_test_consumer_is_executed(self):
        self.java(TEST, "delta/DeltaTest.java", imports=["alpha.service.AlphaService"],
                  body="@TestConfiguration static class Config {}")
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/alpha/service/AlphaService.java")
        plan = self.plan()
        self.assertEqual(plan["mode"], "selected", plan)
        self.assertIn(PACKAGE + ".delta.DeltaTest", plan["tests"])

    def test_class_plan_nested_interface_implementations_keep_runtime_consumers(self):
        self.java(MAIN, "delta/service/DeltaService.java", imports=["alpha/api/Feature".replace("/", ".")],
                  body="public class Nested implements Feature {}")
        self.java(MAIN, "alpha/api/Feature.java", declaration="public interface Feature", body="void execute();")
        self.java(MAIN, "beta/service/BetaService.java", imports=["alpha.api.Feature"])
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/delta/service/DeltaService.java")
        self.assertIn("beta", self.plan()["modules"])

    def test_class_plan_dispatch_keeps_consumers_without_changing_sibling_implementations(self):
        self.java(MAIN, "delta/service/Feature.java", declaration="public interface Feature", body="void execute();")
        self.java(MAIN, "delta/service/DeltaService.java", declaration="public class DeltaService implements Feature")
        # Do not insert an ordinary field use: inheritance alone does not make a sibling use this implementation.
        self.write(f"{MAIN}/alpha/service/AlphaService.java",
                   f"package {PACKAGE}.alpha.service;\nimport {PACKAGE}.delta.service.Feature;\n"
                   'public class AlphaService implements Feature {}')
        self.java(MAIN, "epsilon/service/EpsilonService.java", imports=["delta.service.Feature"])
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/delta/service/DeltaService.java")
        plan = self.plan()
        self.assertNotIn("alpha", plan["modules"])
        self.assertIn("epsilon", plan["modules"])

    def test_class_plan_selects_generic_http_consumers_of_affected_production_module(self):
        self.java(MAIN, "delta/web/DeltaResource.java", declaration='@RestController @RequestMapping("api/delta/") public class DeltaResource')
        self.java(TEST, "alpha/AlphaTest.java", body='void get() { request.get("/api/delta/items/" + id, Map.class); }')
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/delta/web/DeltaResource.java")
        plan = self.plan()
        self.assertEqual(plan["mode"], "selected", plan)
        self.assertIn(PACKAGE + ".alpha.AlphaTest", plan["tests"])

    def test_class_plan_http_consumers_retain_both_revisions_and_helper_subclasses(self):
        self.java(MAIN, "delta/web/DeltaResource.java", declaration='@RestController @RequestMapping("api/delta/") public class DeltaResource')
        self.java(TEST, "shared/base/AbstractHttpTest.java", declaration="public abstract class AbstractHttpTest",
                  body='String getUrl() { return "/api/delta/items/%d"; }')
        self.java(TEST, "alpha/AlphaTest.java", imports=["shared.base.AbstractHttpTest"], declaration="public class AlphaTest extends AbstractHttpTest")
        self.java(TEST, "epsilon/EpsilonTest.java", body='String BASE_URL = "/api/delta/items/";')
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        # Removed callers still matter at base; the changed current test also executes.
        self.java(TEST, "epsilon/EpsilonTest.java")
        self.change(f"{MAIN}/delta/web/DeltaResource.java")
        plan = self.plan()
        self.assertEqual(plan["mode"], "selected", plan)
        self.assertIn(PACKAGE + ".alpha.AlphaTest", plan["tests"])
        self.assertIn(PACKAGE + ".epsilon.EpsilonTest", plan["tests"])

    def test_class_plan_http_constant_helpers_keep_consumers_and_subclasses(self):
        self.java(TEST, "shared/util/Routes.java", body='public static final String API_PREFIX = "/api/delta/items/";')
        self.write(f"{TEST}/alpha/AlphaTest.java",
                   f"package {PACKAGE}.alpha;\nimport {PACKAGE}.shared.util.Routes;\n"
                   'public class AlphaTest { String url = Routes.API_PREFIX; }')
        self.java(TEST, "epsilon/EpsilonTest.java", imports=["alpha.AlphaTest"], declaration="public class EpsilonTest extends AlphaTest")
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/delta/service/DeltaService.java")
        plan = self.plan()
        self.assertEqual(plan["mode"], "selected", plan)
        self.assertIn(PACKAGE + ".alpha.AlphaTest", plan["tests"])
        self.assertIn(PACKAGE + ".epsilon.EpsilonTest", plan["tests"])

    def test_class_plan_unknown_http_target_is_not_hidden_by_other_known_calls(self):
        self.java(TEST, "alpha/AlphaTest.java", body='void call(String target) { request.get("/api/alpha/items", Map.class); request.delete(target, String.class); }')
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/delta/service/DeltaService.java")
        self.assertIn(PACKAGE + ".alpha.AlphaTest", self.plan()["tests"])

    def test_class_plan_servlet_support_import_is_not_an_http_caller(self):
        self.write(f"{TEST}/shared/util/RequestProcessor.java",
                   f"package {PACKAGE}.shared.util;\n"
                   "import org.springframework.test.web.servlet.request.RequestPostProcessor;\n"
                   "public class RequestProcessor implements RequestPostProcessor {}")
        self.java(TEST, "alpha/AlphaTest.java", imports=["shared.util.RequestProcessor"])
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/delta/service/DeltaService.java")
        self.assertNotIn(PACKAGE + ".alpha.AlphaTest", self.plan()["tests"])

    def test_class_plan_external_mock_url_does_not_become_global_http_consumer(self):
        self.java(TEST, "shared/util/ExternalMockProvider.java", body='String endpoint = "/api/v1/convert";')
        self.java(TEST, "alpha/AlphaTest.java", imports=["shared.util.ExternalMockProvider"])
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/delta/service/DeltaService.java")
        self.assertNotIn(PACKAGE + ".alpha.AlphaTest", self.plan()["tests"])

    def test_class_plan_fixed_class_namespace_bounds_computed_method_path(self):
        self.java(MAIN, "delta/web/DeltaResource.java",
                  declaration='@RestController @RequestMapping("api/delta/") public class DeltaResource',
                  body='@DeleteMapping("items/{login:" + Constants.LOGIN_REGEX + "}") void delete() {}')
        self.java(TEST, "alpha/AlphaTest.java", body='void call() { request.delete("/api/delta/items/user", String.class); }')
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/delta/web/DeltaResource.java")
        plan = self.plan()
        self.assertEqual(plan["mode"], "selected", plan)
        self.assertIn(PACKAGE + ".alpha.AlphaTest", plan["tests"])

    def test_class_plan_runtime_class_path_placeholder_is_unclassified(self):
        self.java(MAIN, "delta/web/DeltaResource.java",
                  declaration='@RestController @RequestMapping("${controller.base}") public class DeltaResource')
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/delta/web/DeltaResource.java")
        self.assertEqual(self.plan()["mode"], "full")

    def test_class_plan_mapping_metadata_does_not_prove_an_unknown_route(self):
        self.java(MAIN, "delta/web/PublicResource.java",
                  declaration='@RestController @RequestMapping(value = UNKNOWN_PATH, produces = "application/json") public class PublicResource')
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/delta/web/PublicResource.java")
        self.assertEqual(self.plan()["mode"], "full")

    def test_class_plan_literal_mapping_does_not_hide_an_unknown_route_alias(self):
        self.java(MAIN, "delta/web/DeltaResource.java",
                  declaration='@RestController @RequestMapping({"api/delta/", LEGACY_ALIAS}) public class DeltaResource')
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/delta/web/DeltaResource.java")
        self.assertEqual(self.plan()["mode"], "full")

    def test_class_plan_unrelated_unknown_controller_does_not_invalidate_module(self):
        self.java(MAIN, "delta/web/UnrelatedResource.java",
                  declaration='@RestController @RequestMapping(UNKNOWN_PATH) public class UnrelatedResource')
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/delta/service/DeltaService.java")
        self.assertEqual(self.plan()["mode"], "selected")

    def test_class_plan_literal_public_routes_do_not_invalidate_whole_module(self):
        self.java(MAIN, "delta/web/PublicResource.java",
                  declaration='@RestController @RequestMapping("/.well-known/association") public class PublicResource')
        self.java(TEST, "alpha/AlphaTest.java", body='void call() { request.get("/.well-known/association", Map.class); }')
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/delta/web/PublicResource.java")
        plan = self.plan()
        self.assertEqual(plan["mode"], "selected", plan)
        self.assertIn(PACKAGE + ".alpha.AlphaTest", plan["tests"])

    def test_class_plan_static_builder_unknown_call_is_not_hidden_by_known_call(self):
        self.write(f"{TEST}/alpha/AlphaTest.java", f"package {PACKAGE}.alpha;\n"
                   "import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;\n"
                   'public class AlphaTest { void call(String target) { get("/api/alpha/items"); get(target); } }')
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/delta/service/DeltaService.java")
        self.assertIn(PACKAGE + ".alpha.AlphaTest", self.plan()["tests"])

    def test_class_plan_transformed_http_prefix_remains_unclassified(self):
        self.java(TEST, "alpha/AlphaTest.java", body='void call() { request.get("/api/alpha/items".replace("alpha", "delta"), Map.class); }')
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/delta/service/DeltaService.java")
        self.assertIn(PACKAGE + ".alpha.AlphaTest", self.plan()["tests"])

    def test_class_plan_known_local_url_constants_do_not_add_unrelated_http_tests(self):
        self.java(TEST, "alpha/AlphaTest.java", body='String BASE_URL = "/api/alpha/items/"; void call() { request.get(BASE_URL + id, Map.class); }')
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/delta/service/DeltaService.java")
        self.assertNotIn(PACKAGE + ".alpha.AlphaTest", self.plan()["tests"])

    def test_class_plan_unclassified_dynamic_http_consumers_are_included(self):
        self.java(TEST, "alpha/AlphaTest.java", body='void get() { request.get("/api/" + module + "/items", Map.class); }')
        self.java(TEST, "epsilon/EpsilonTest.java", body='void get(String target) { request.get(target, String.class); }')
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/delta/service/DeltaService.java")
        plan = self.plan()
        self.assertEqual(plan["mode"], "selected", plan)
        self.assertIn(PACKAGE + ".alpha.AlphaTest", plan["tests"])
        self.assertIn(PACKAGE + ".epsilon.EpsilonTest", plan["tests"])

    def test_class_plan_dynamic_publisher_consumer_still_requires_full_suite(self):
        self.java(MAIN, "beta/service/BetaService.java", imports=["alpha.service.AlphaService"],
                  body="void send() { publisher.publishEvent(event); }")
        self.commit()
        self.git("branch", "-f", "base", "HEAD")
        self.change(f"{MAIN}/alpha/service/AlphaService.java")
        self.assertEqual(self.plan()["mode"], "full")

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

    def test_stylelint_configuration_does_not_expand_service_selection(self):
        for path in ("config/stylelint/stylelint.config.json", "config/stylelint/stylelint.config.js"):
            with self.subTest(path=path):
                self.write(path, "Stylelint configuration")
                self.git("add", path)
                self.change(f"{MAIN}/delta/service/DeltaService.java")
                plan = self.plan()
                self.assertEqual(plan["mode"], "selected", plan)
                self.assertEqual(plan["modules"], ["delta"])
                self.assertEqual(plan["tests"], [PACKAGE + ".delta.DeltaTest"])
                self.git("reset", "-q", "--", path)
                (self.root / path).unlink()

    def test_unknown_stylelint_directory_file_still_requires_full_server_validation(self):
        self.write("config/stylelint/unknown-server-input.json", "Unknown configuration")
        self.git("add", "--all")
        self.change(f"{MAIN}/delta/service/DeltaService.java")
        self.assertEqual(self.plan()["mode"], "full")

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
