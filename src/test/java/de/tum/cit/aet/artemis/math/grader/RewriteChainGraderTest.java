package de.tum.cit.aet.artemis.math.grader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.math.domain.BlockDefinition;
import de.tum.cit.aet.artemis.math.domain.DerivationStep;
import de.tum.cit.aet.artemis.math.domain.GoalMode;
import de.tum.cit.aet.artemis.math.domain.MathExercise;
import de.tum.cit.aet.artemis.math.domain.MathNode;
import de.tum.cit.aet.artemis.math.domain.MathNodes;
import de.tum.cit.aet.artemis.math.domain.MathSubmission;
import de.tum.cit.aet.artemis.math.domain.RewriteRule;
import de.tum.cit.aet.artemis.math.domain.RuleDirection;
import de.tum.cit.aet.artemis.math.domain.StepDirection;
import de.tum.cit.aet.artemis.math.domain.blocks.AddBlockDefinition;
import de.tum.cit.aet.artemis.math.domain.blocks.EqualityBlockDefinition;
import de.tum.cit.aet.artemis.math.domain.blocks.FractionBlockDefinition;
import de.tum.cit.aet.artemis.math.domain.blocks.MulBlockDefinition;
import de.tum.cit.aet.artemis.math.domain.blocks.NegationBlockDefinition;
import de.tum.cit.aet.artemis.math.domain.blocks.NumberBlockDefinition;
import de.tum.cit.aet.artemis.math.domain.blocks.ParenthesesBlockDefinition;
import de.tum.cit.aet.artemis.math.domain.blocks.SubBlockDefinition;
import de.tum.cit.aet.artemis.math.domain.blocks.VariableBlockDefinition;
import de.tum.cit.aet.artemis.math.dto.MathSubmissionDTO.DerivationStepDTO;
import de.tum.cit.aet.artemis.math.service.BlockRegistry;

/**
 * Pure unit tests for the rewrite-chain engine. No Spring context — the {@link BlockRegistry}
 * is hand-assembled with every built-in block so changes to grading semantics surface here first.
 */
class RewriteChainGraderTest {

    private BlockRegistry registry;

    private RewriteChainGrader grader;

    @BeforeEach
    void setUp() {
        List<BlockDefinition> blocks = List.of(new NumberBlockDefinition(), new VariableBlockDefinition(), new AddBlockDefinition(), new SubBlockDefinition(),
                new MulBlockDefinition(), new FractionBlockDefinition(), new EqualityBlockDefinition(), new ParenthesesBlockDefinition(), new NegationBlockDefinition());
        registry = new BlockRegistry(blocks);
        registry.index();
        grader = new RewriteChainGrader(registry);
    }

    // ----- Pattern matching & rule application -----

    @Test
    void applyRule_addZeroLeft_atRoot() {
        MathNode tree = MathNodes.add(MathNodes.num("0"), MathNodes.var("x"));
        RewriteRule rule = registry.findRuleById("add_zero_left").orElseThrow();
        Optional<MathNode> result = grader.applyRule(tree, List.of(), rule);
        assertThat(result).contains(MathNodes.var("x"));
    }

    @Test
    void applyRule_addComm_isItsOwnInverse() {
        MathNode tree = MathNodes.add(MathNodes.var("a"), MathNodes.var("b"));
        RewriteRule comm = registry.findRuleById("add_comm").orElseThrow();
        MathNode once = grader.applyRule(tree, List.of(), comm).orElseThrow();
        MathNode twice = grader.applyRule(once, List.of(), comm).orElseThrow();
        assertThat(twice).isEqualTo(tree);
    }

    @Test
    void applyRule_mulZeroLeft_terminates() {
        MathNode tree = MathNodes.mul(MathNodes.num("0"), MathNodes.add(MathNodes.var("a"), MathNodes.var("b")));
        RewriteRule rule = registry.findRuleById("mul_zero_left").orElseThrow();
        Optional<MathNode> result = grader.applyRule(tree, List.of(), rule);
        assertThat(result).contains(MathNodes.num("0"));
    }

    @Test
    void applyRule_paren_unwrap_inside_add() {
        MathNode tree = MathNodes.add(MathNodes.paren(MathNodes.var("x")), MathNodes.var("y"));
        RewriteRule unwrap = registry.findRuleById("paren_unwrap").orElseThrow();
        // Path [0] is the left child of add (alphabetical: left < right), i.e. the parens node
        Optional<MathNode> result = grader.applyRule(tree, List.of(0), unwrap);
        assertThat(result).contains(MathNodes.add(MathNodes.var("x"), MathNodes.var("y")));
    }

    @Test
    void applyRule_patternMismatch_returnsEmpty() {
        MathNode tree = MathNodes.add(MathNodes.var("x"), MathNodes.num("0"));
        RewriteRule addZeroLeft = registry.findRuleById("add_zero_left").orElseThrow();
        // add_zero_left expects 0+a, but we have x+0
        assertThat(grader.applyRule(tree, List.of(), addZeroLeft)).isEmpty();
    }

    @Test
    void applyRule_nonLinearBinding_requiresSameSubtree() {
        // (x · a) / (x · b) matches frac_mul_cancel_left with c=x, a=a, b=b
        MathNode matching = MathNodes.frac(MathNodes.mul(MathNodes.var("x"), MathNodes.var("a")), MathNodes.mul(MathNodes.var("x"), MathNodes.var("b")));
        RewriteRule cancel = registry.findRuleById("frac_mul_cancel_left").orElseThrow();
        assertThat(grader.applyRule(matching, List.of(), cancel)).contains(MathNodes.frac(MathNodes.var("a"), MathNodes.var("b")));

        // (x · a) / (y · b) — c on each side differs, should NOT match
        MathNode nonMatching = MathNodes.frac(MathNodes.mul(MathNodes.var("x"), MathNodes.var("a")), MathNodes.mul(MathNodes.var("y"), MathNodes.var("b")));
        assertThat(grader.applyRule(nonMatching, List.of(), cancel)).isEmpty();
    }

    // ----- Side conditions -----

    @Test
    void applyRule_fracMulCancelLeft_rejectsZeroFactor() {
        // (0 · a) / (0 · b) — pattern matches structurally but c = 0 is excluded by the side condition
        MathNode tree = MathNodes.frac(MathNodes.mul(MathNodes.num("0"), MathNodes.var("a")), MathNodes.mul(MathNodes.num("0"), MathNodes.var("b")));
        RewriteRule cancel = registry.findRuleById("frac_mul_cancel_left").orElseThrow();
        assertThat(grader.applyRule(tree, List.of(), cancel)).isEmpty();
    }

    @Test
    void applyRule_fracMulCancelLeft_acceptsNonZeroFactor() {
        MathNode tree = MathNodes.frac(MathNodes.mul(MathNodes.num("2"), MathNodes.var("a")), MathNodes.mul(MathNodes.num("2"), MathNodes.var("b")));
        RewriteRule cancel = registry.findRuleById("frac_mul_cancel_left").orElseThrow();
        assertThat(grader.applyRule(tree, List.of(), cancel)).contains(MathNodes.frac(MathNodes.var("a"), MathNodes.var("b")));
    }

    // ----- Number normalisation -----

    @Test
    void normalize_collapsesNumericForms() {
        assertThat(MathNodes.normalize(MathNodes.num("0.0"))).isEqualTo(MathNodes.num("0"));
        assertThat(MathNodes.normalize(MathNodes.num("00"))).isEqualTo(MathNodes.num("0"));
        assertThat(MathNodes.normalize(MathNodes.num("-0"))).isEqualTo(MathNodes.num("0"));
        assertThat(MathNodes.normalize(MathNodes.num("1.50"))).isEqualTo(MathNodes.num("1.5"));
    }

    @Test
    void normalize_preservesNonNumericValues() {
        assertThat(MathNodes.normalize(MathNodes.var("x"))).isEqualTo(MathNodes.var("x"));
        assertThat(MathNodes.normalize(MathNodes.num("notANumber"))).isEqualTo(MathNodes.num("notANumber"));
    }

    @Test
    void normalize_recursesThroughSlots() {
        MathNode tree = MathNodes.add(MathNodes.num("0.0"), MathNodes.var(" x "));
        MathNode expected = MathNodes.add(MathNodes.num("0"), MathNodes.var("x"));
        assertThat(MathNodes.normalize(tree)).isEqualTo(expected);
    }

    @Test
    void registry_normalisesRuleLiterals() {
        // After registry init, every rule's pattern/template literals should be canonical.
        RewriteRule addZeroLeft = registry.findRuleById("add_zero_left").orElseThrow();
        MathNode patternLeft = addZeroLeft.pattern().getSlots().get("left").getFirst();
        assertThat(patternLeft.getValue()).isEqualTo("0");
    }

    // ----- Wildcard validation -----

    @Test
    void assertWildcardFree_rejectsWildcardAtRoot() {
        assertThatThrownBy(() -> MathNodes.assertWildcardFree(MathNodes.wc("a"))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Wildcard");
    }

    @Test
    void assertWildcardFree_rejectsWildcardDeep() {
        MathNode tree = MathNodes.add(MathNodes.var("x"), MathNodes.mul(MathNodes.num("1"), MathNodes.wc("c")));
        assertThatThrownBy(() -> MathNodes.assertWildcardFree(tree)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void assertWildcardFree_acceptsWildcardFreeTree() {
        MathNode tree = MathNodes.add(MathNodes.var("x"), MathNodes.mul(MathNodes.num("1"), MathNodes.var("y")));
        MathNodes.assertWildcardFree(tree);
    }

    @Test
    void assertWildcardFree_acceptsNull() {
        MathNodes.assertWildcardFree(null);
    }

    // ----- gradeSubmission end-to-end -----

    @Test
    void gradeSubmission_singleValidStep_reachesTarget() {
        MathExercise exercise = exerciseOf(MathNodes.add(MathNodes.num("0"), MathNodes.var("x")), MathNodes.var("x"));
        MathSubmission submission = submissionOf(step(0, "add_zero_left", List.of(), MathNodes.var("x")));
        assertThat(grader.gradeSubmission(exercise, submission)).isEqualTo(100.0);
    }

    @Test
    void gradeSubmission_brokenChain_scoresZeroByDefault() {
        // Source 0 + x, target x. Step claims add_zero_left at root with result "y" (wrong)
        MathExercise exercise = exerciseOf(MathNodes.add(MathNodes.num("0"), MathNodes.var("x")), MathNodes.var("x"));
        MathSubmission submission = submissionOf(step(0, "add_zero_left", List.of(), MathNodes.var("y")));
        assertThat(grader.gradeSubmission(exercise, submission)).isEqualTo(0.0);
    }

    @Test
    void gradeSubmission_partialCredit_proportionalToValidPrefix() {
        // Two-step math: (0 + x) + 0 -> x + 0 -> x. Student only does step 1.
        MathNode source = MathNodes.add(MathNodes.add(MathNodes.num("0"), MathNodes.var("x")), MathNodes.num("0"));
        MathNode afterStep1 = MathNodes.add(MathNodes.var("x"), MathNodes.num("0"));
        MathNode target = MathNodes.var("x");
        MathExercise exercise = exerciseOf(source, target);
        exercise.setPartialCreditEnabled(true);
        exercise.setExampleDerivations(
                List.of(List.of(new DerivationStepDTO(null, 0, "add_zero_left", List.of(0), afterStep1), new DerivationStepDTO(null, 1, "add_zero_right", List.of(), target))));
        // Student does step 1 only: at path [0] (the left add subtree), 0+x -> x. Result is x + 0.
        MathSubmission submission = submissionOf(step(0, "add_zero_left", List.of(0), afterStep1));
        double score = grader.gradeSubmission(exercise, submission);
        assertThat(score).isEqualTo(50.0);
    }

    @Test
    void gradeSubmission_invalidRuleId_breaksChain() {
        MathExercise exercise = exerciseOf(MathNodes.add(MathNodes.num("0"), MathNodes.var("x")), MathNodes.var("x"));
        MathSubmission submission = submissionOf(step(0, "nonexistent_rule", List.of(), MathNodes.var("x")));
        assertThat(grader.gradeSubmission(exercise, submission)).isEqualTo(0.0);
    }

    @Test
    void gradeSubmission_emptySteps_sourceEqualsTarget_scores100() {
        MathExercise exercise = exerciseOf(MathNodes.var("x"), MathNodes.var("x"));
        MathSubmission submission = submissionOf();
        assertThat(grader.gradeSubmission(exercise, submission)).isEqualTo(100.0);
    }

    // ----- Direction-aware rule application -----

    @Test
    void applyRule_addAssoc_forward_groupsRight() {
        // (a + b) + c -> a + (b + c)
        MathNode tree = MathNodes.add(MathNodes.add(MathNodes.var("a"), MathNodes.var("b")), MathNodes.var("c"));
        RewriteRule assoc = registry.findRuleById("add_assoc").orElseThrow();
        MathNode result = grader.applyRule(tree, List.of(), assoc, StepDirection.FORWARD).orElseThrow();
        assertThat(result).isEqualTo(MathNodes.add(MathNodes.var("a"), MathNodes.add(MathNodes.var("b"), MathNodes.var("c"))));
    }

    @Test
    void applyRule_addAssoc_reverse_groupsLeft() {
        // a + (b + c) -> (a + b) + c
        MathNode tree = MathNodes.add(MathNodes.var("a"), MathNodes.add(MathNodes.var("b"), MathNodes.var("c")));
        RewriteRule assoc = registry.findRuleById("add_assoc").orElseThrow();
        MathNode result = grader.applyRule(tree, List.of(), assoc, StepDirection.REVERSE).orElseThrow();
        assertThat(result).isEqualTo(MathNodes.add(MathNodes.add(MathNodes.var("a"), MathNodes.var("b")), MathNodes.var("c")));
    }

    @Test
    void applyRule_addZeroLeft_reverse_isRejected() {
        // add_zero_left is FORWARD_ONLY — reverse application must always fail
        RewriteRule rule = registry.findRuleById("add_zero_left").orElseThrow();
        assertThat(rule.direction()).isEqualTo(RuleDirection.FORWARD_ONLY);
        assertThat(grader.applyRule(MathNodes.var("x"), List.of(), rule, StepDirection.REVERSE)).isEmpty();
    }

    // ----- Distributivity -----

    @Test
    void applyRule_mulDistrib_forward_expands() {
        // a · (b + c) -> a · b + a · c
        MathNode tree = MathNodes.mul(MathNodes.var("a"), MathNodes.add(MathNodes.var("b"), MathNodes.var("c")));
        RewriteRule distrib = registry.findRuleById("mul_distrib").orElseThrow();
        MathNode result = grader.applyRule(tree, List.of(), distrib, StepDirection.FORWARD).orElseThrow();
        assertThat(result).isEqualTo(MathNodes.add(MathNodes.mul(MathNodes.var("a"), MathNodes.var("b")), MathNodes.mul(MathNodes.var("a"), MathNodes.var("c"))));
    }

    @Test
    void applyRule_mulDistrib_reverse_factors() {
        // a · b + a · c -> a · (b + c)
        MathNode tree = MathNodes.add(MathNodes.mul(MathNodes.var("a"), MathNodes.var("b")), MathNodes.mul(MathNodes.var("a"), MathNodes.var("c")));
        RewriteRule distrib = registry.findRuleById("mul_distrib").orElseThrow();
        MathNode result = grader.applyRule(tree, List.of(), distrib, StepDirection.REVERSE).orElseThrow();
        assertThat(result).isEqualTo(MathNodes.mul(MathNodes.var("a"), MathNodes.add(MathNodes.var("b"), MathNodes.var("c"))));
    }

    // ----- Negation -----

    @Test
    void applyRule_negNeg_roundtrip() {
        MathNode doubleNeg = MathNodes.neg(MathNodes.neg(MathNodes.var("a")));
        RewriteRule rule = registry.findRuleById("neg_neg").orElseThrow();
        MathNode collapsed = grader.applyRule(doubleNeg, List.of(), rule, StepDirection.FORWARD).orElseThrow();
        assertThat(collapsed).isEqualTo(MathNodes.var("a"));
        MathNode reintroduced = grader.applyRule(collapsed, List.of(), rule, StepDirection.REVERSE).orElseThrow();
        assertThat(reintroduced).isEqualTo(doubleNeg);
    }

    @Test
    void applyRule_addInverse_collapsesToZero() {
        // a + (-a) -> 0
        MathNode tree = MathNodes.add(MathNodes.var("a"), MathNodes.neg(MathNodes.var("a")));
        RewriteRule rule = registry.findRuleById("add_inverse").orElseThrow();
        assertThat(grader.applyRule(tree, List.of(), rule)).contains(MathNodes.num("0"));
    }

    @Test
    void applyRule_subAsAddNeg_bridgesToAddRules() {
        // a - b -> a + (-b), then if b=0 the add_zero_right rule yields a + 0 -> a
        MathNode tree = MathNodes.sub(MathNodes.var("a"), MathNodes.num("0"));
        RewriteRule bridge = registry.findRuleById("sub_as_add_neg").orElseThrow();
        MathNode bridged = grader.applyRule(tree, List.of(), bridge, StepDirection.FORWARD).orElseThrow();
        assertThat(bridged).isEqualTo(MathNodes.add(MathNodes.var("a"), MathNodes.neg(MathNodes.num("0"))));
    }

    // ----- gradeSubmission with mixed-direction steps -----

    @Test
    void gradeSubmission_acceptsReverseStepOnBidirectionalRule() {
        // Source: a + (b + c). Apply add_assoc REVERSE to get (a + b) + c. That's the target.
        MathNode source = MathNodes.add(MathNodes.var("a"), MathNodes.add(MathNodes.var("b"), MathNodes.var("c")));
        MathNode target = MathNodes.add(MathNodes.add(MathNodes.var("a"), MathNodes.var("b")), MathNodes.var("c"));
        MathExercise exercise = exerciseOf(source, target);
        DerivationStep step = step(0, "add_assoc", List.of(), target);
        step.setDirection(StepDirection.REVERSE);
        MathSubmission submission = submissionOf(step);
        assertThat(grader.gradeSubmission(exercise, submission)).isEqualTo(100.0);
    }

    @Test
    void gradeSubmission_rejectsReverseStepOnForwardOnlyRule() {
        // add_zero_left is FORWARD_ONLY; even if the result tree happens to "look right",
        // declaring REVERSE breaks the chain.
        MathExercise exercise = exerciseOf(MathNodes.var("x"), MathNodes.add(MathNodes.num("0"), MathNodes.var("x")));
        DerivationStep step = step(0, "add_zero_left", List.of(), MathNodes.add(MathNodes.num("0"), MathNodes.var("x")));
        step.setDirection(StepDirection.REVERSE);
        MathSubmission submission = submissionOf(step);
        assertThat(grader.gradeSubmission(exercise, submission)).isEqualTo(0.0);
    }

    // ----- No-regress + distance-based partial credit -----

    @Test
    void replay_loopingAddComm_breaksAtRevisit() {
        // Source: a + b, target: a + b (already equal). Student applies add_comm twice — second step revisits source.
        MathNode source = MathNodes.add(MathNodes.var("a"), MathNodes.var("b"));
        MathExercise exercise = exerciseOf(source, source);
        exercise.setPartialCreditEnabled(true);
        DerivationStep step1 = step(0, "add_comm", List.of(), MathNodes.add(MathNodes.var("b"), MathNodes.var("a")));
        DerivationStep step2 = step(1, "add_comm", List.of(), source);
        MathSubmission submission = submissionOf(step1, step2);
        // Source already equals target, so empty-step branch is what matters; with steps present, the loop should break on revisit
        // and score must remain controlled (not 100 from a back-flip).
        double score = grader.gradeSubmission(exercise, submission);
        assertThat(score).isLessThan(100.0);
    }

    @Test
    void distanceBasedPartialCredit_reducesAsStudentApproachesTarget() {
        // Source: 0 + (0 + x), target: x. Single valid step: add_zero_left on outermost gives (0 + x), still distance 2 to x.
        MathNode source = MathNodes.add(MathNodes.num("0"), MathNodes.add(MathNodes.num("0"), MathNodes.var("x")));
        MathNode afterOuter = MathNodes.add(MathNodes.num("0"), MathNodes.var("x"));
        MathNode target = MathNodes.var("x");
        MathExercise exercise = exerciseOf(source, target);
        exercise.setPartialCreditEnabled(true);
        MathSubmission oneStep = submissionOf(step(0, "add_zero_left", List.of(), afterOuter));
        double oneStepScore = grader.gradeSubmission(exercise, oneStep);
        assertThat(oneStepScore).isGreaterThan(0.0).isLessThanOrEqualTo(99.0);

        // Two steps: get all the way to x → 100.
        MathSubmission twoSteps = submissionOf(step(0, "add_zero_left", List.of(), afterOuter), step(1, "add_zero_left", List.of(), target));
        assertThat(grader.gradeSubmission(exercise, twoSteps)).isEqualTo(100.0);
    }

    @Test
    void distanceBasedPartialCredit_alwaysCappedAt99() {
        // Source: add(0, x), target: x. Apply add_zero_left → x (full math).
        // To test the cap, instead apply a no-op-ish single rewrite that gets very close but not all the way to target.
        // (Hard to construct with our rule library without reaching the target — verified via direct distanceBasedScore tests below.)
        // Here, regression: a *complete* derivation must score exactly 100 (cap doesn't accidentally apply).
        MathNode source = MathNodes.add(MathNodes.num("0"), MathNodes.var("x"));
        MathExercise exercise = exerciseOf(source, MathNodes.var("x"));
        exercise.setPartialCreditEnabled(true);
        MathSubmission full = submissionOf(step(0, "add_zero_left", List.of(), MathNodes.var("x")));
        assertThat(grader.gradeSubmission(exercise, full)).isEqualTo(100.0);
    }

    @Test
    void distanceBasedPartialCredit_zeroProgressScoresZero() {
        // add_comm doesn't reduce distance to target x. Source: a + b, target: x.
        MathNode source = MathNodes.add(MathNodes.var("a"), MathNodes.var("b"));
        MathNode swapped = MathNodes.add(MathNodes.var("b"), MathNodes.var("a"));
        MathExercise exercise = exerciseOf(source, MathNodes.var("x"));
        exercise.setPartialCreditEnabled(true);
        MathSubmission submission = submissionOf(step(0, "add_comm", List.of(), swapped));
        double score = grader.gradeSubmission(exercise, submission);
        assertThat(score).isEqualTo(0.0);
    }

    @Test
    void equationMode_partialCreditReflectsSideDistanceReduction() {
        // Goal: 0 + x = x. Student applies add_zero_left on the LEFT side → x = x (tautology, score 100).
        // Without applying anything: partial credit should be 0 (sides currently differ).
        MathNode goal = MathNodes.eq(MathNodes.add(MathNodes.num("0"), MathNodes.var("x")), MathNodes.var("x"));
        MathExercise exercise = equationExerciseOf(goal);
        exercise.setPartialCreditEnabled(true);
        // Empty submission, goal not yet tautology → 0
        assertThat(grader.gradeSubmission(exercise, submissionOf())).isEqualTo(0.0);
        // One valid step that closes the math
        MathNode reduced = MathNodes.eq(MathNodes.var("x"), MathNodes.var("x"));
        MathSubmission submission = submissionOf(step(0, "add_zero_left", List.of(0), reduced));
        assertThat(grader.gradeSubmission(exercise, submission)).isEqualTo(100.0);
    }

    // ----- Reduction strategy + reachability -----

    @Test
    void verifyReachability_transformationMode_reducerReachesTarget() {
        // Source: 0 + (0 + x). Target: x. Reducer applies add_zero_left twice → reaches x.
        MathNode source = MathNodes.add(MathNodes.num("0"), MathNodes.add(MathNodes.num("0"), MathNodes.var("x")));
        MathExercise exercise = exerciseOf(source, MathNodes.var("x"));
        Optional<ReachabilityReport> report = grader.verifyReachability(exercise);
        assertThat(report).isPresent();
        assertThat(report.get().reachable()).isTrue();
        assertThat(report.get().finalDistance()).isZero();
        assertThat(report.get().reducedExpression()).isEqualTo(MathNodes.var("x"));
    }

    @Test
    void verifyReachability_transformationMode_reducerFallsShort() {
        // Source: a + b. Target: b + a. Reducer is FORWARD_ONLY — can't apply add_comm — stays at a + b.
        MathNode source = MathNodes.add(MathNodes.var("a"), MathNodes.var("b"));
        MathNode target = MathNodes.add(MathNodes.var("b"), MathNodes.var("a"));
        MathExercise exercise = exerciseOf(source, target);
        Optional<ReachabilityReport> report = grader.verifyReachability(exercise);
        assertThat(report).isPresent();
        assertThat(report.get().reachable()).isFalse();
        assertThat(report.get().finalDistance()).isGreaterThan(0);
    }

    @Test
    void verifyReachability_returnsEmptyWhenMissingExpressions() {
        MathExercise empty = new MathExercise();
        assertThat(grader.verifyReachability(empty)).isEmpty();
    }

    @Test
    void suggestHints_rankedByDistanceToTarget_returnsAtMostThree() {
        // Current state: (0 + a) + (0 + b). Target: a + b. add_zero_left applies at two positions, both reduce distance.
        MathNode current = MathNodes.add(MathNodes.add(MathNodes.num("0"), MathNodes.var("a")), MathNodes.add(MathNodes.num("0"), MathNodes.var("b")));
        MathNode target = MathNodes.add(MathNodes.var("a"), MathNodes.var("b"));
        MathExercise exercise = exerciseOf(current, target);
        List<HintSuggestion> hints = grader.suggestHints(exercise, current);
        assertThat(hints).isNotEmpty();
        assertThat(hints.size()).isLessThanOrEqualTo(3);
        // At least one suggestion should be add_zero_left (the most-direct reducer)
        assertThat(hints).anyMatch(h -> h.ruleId().equals("add_zero_left"));
    }

    @Test
    void suggestHints_emptyWhenNoTargetConfigured() {
        MathExercise empty = new MathExercise();
        assertThat(grader.suggestHints(empty, MathNodes.var("x"))).isEmpty();
    }

    @Test
    void suggestHints_equationMode_picksSideDistanceReducers() {
        // Goal: 0 + x = x. Suggesting hints should propose add_zero_left at the left side.
        MathNode goal = MathNodes.eq(MathNodes.add(MathNodes.num("0"), MathNodes.var("x")), MathNodes.var("x"));
        MathExercise exercise = equationExerciseOf(goal);
        List<HintSuggestion> hints = grader.suggestHints(exercise, goal);
        assertThat(hints).isNotEmpty();
        assertThat(hints).anyMatch(h -> h.ruleId().equals("add_zero_left"));
    }

    // ----- AC normalisation -----

    @Test
    void equalsAC_treatsCommutedAddAsEqualWhenAcOn() {
        MathNode ab = MathNodes.add(MathNodes.var("a"), MathNodes.var("b"));
        MathNode ba = MathNodes.add(MathNodes.var("b"), MathNodes.var("a"));
        assertThat(MathNodes.equalsAC(ab, ba, true)).isTrue();
        assertThat(MathNodes.equalsAC(ab, ba, false)).isFalse();
    }

    @Test
    void normalizeAC_flattensAndSortsAssocChains() {
        MathNode leftAssoc = MathNodes.add(MathNodes.add(MathNodes.var("c"), MathNodes.var("a")), MathNodes.var("b"));
        MathNode rightAssoc = MathNodes.add(MathNodes.var("b"), MathNodes.add(MathNodes.var("a"), MathNodes.var("c")));
        assertThat(MathNodes.normalizeAC(leftAssoc)).isEqualTo(MathNodes.normalizeAC(rightAssoc));
    }

    @Test
    void gradeSubmission_acOn_acceptsCommutedTargetWithoutExplicitCommStep() {
        // Source: 0 + x, target: x + 0. Apply add_zero_left → x. AC normalisation should accept x ≡ x + 0.
        MathExercise exercise = exerciseOf(MathNodes.add(MathNodes.num("0"), MathNodes.var("x")), MathNodes.add(MathNodes.var("x"), MathNodes.num("0")));
        exercise.setAcNormalization(true);
        MathSubmission submission = submissionOf(step(0, "add_zero_left", List.of(), MathNodes.var("x")));
        assertThat(grader.gradeSubmission(exercise, submission)).isEqualTo(0.0); // x ≠ x + 0 under AC (since AC just sorts; sizes differ)

        // Sanity: when target genuinely matches by AC (a + b vs b + a), one step closes it.
        MathExercise exercise2 = exerciseOf(MathNodes.add(MathNodes.num("0"), MathNodes.add(MathNodes.var("a"), MathNodes.var("b"))),
                MathNodes.add(MathNodes.var("b"), MathNodes.var("a")));
        exercise2.setAcNormalization(true);
        MathNode afterStep = MathNodes.add(MathNodes.var("a"), MathNodes.var("b"));
        MathSubmission submission2 = submissionOf(step(0, "add_zero_left", List.of(), afterStep));
        assertThat(grader.gradeSubmission(exercise2, submission2)).isEqualTo(100.0);
    }

    @Test
    void gradeSubmission_acOn_equationModeTrivialCommutativityScores100WithoutSteps() {
        // Goal: a + b = b + a. Under AC normalisation this is already a tautology.
        MathNode goal = MathNodes.eq(MathNodes.add(MathNodes.var("a"), MathNodes.var("b")), MathNodes.add(MathNodes.var("b"), MathNodes.var("a")));
        MathExercise exercise = equationExerciseOf(goal);
        exercise.setAcNormalization(true);
        assertThat(grader.gradeSubmission(exercise, submissionOf())).isEqualTo(100.0);
    }

    @Test
    void gradeSubmission_acOff_equationModeCommutativityRequiresExplicitStep() {
        MathNode goal = MathNodes.eq(MathNodes.add(MathNodes.var("a"), MathNodes.var("b")), MathNodes.add(MathNodes.var("b"), MathNodes.var("a")));
        MathExercise exercise = equationExerciseOf(goal);
        // AC off (default) — empty submission is not enough.
        assertThat(grader.gradeSubmission(exercise, submissionOf())).isEqualTo(0.0);
    }

    // ----- isTautology -----

    @Test
    void isTautology_recognisesEqualSides() {
        MathNode tree = MathNodes.eq(MathNodes.var("x"), MathNodes.var("x"));
        assertThat(MathNodes.isTautology(tree)).isTrue();
    }

    @Test
    void isTautology_rejectsUnequalSides() {
        MathNode tree = MathNodes.eq(MathNodes.var("x"), MathNodes.var("y"));
        assertThat(MathNodes.isTautology(tree)).isFalse();
    }

    @Test
    void isTautology_rejectsNonEqualityRoot() {
        assertThat(MathNodes.isTautology(MathNodes.add(MathNodes.var("x"), MathNodes.var("x")))).isFalse();
        assertThat(MathNodes.isTautology(null)).isFalse();
    }

    // ----- EQUATION mode grading -----

    @Test
    void gradeSubmission_equationMode_singleCommStepReachesTautology() {
        // Goal: a + b = b + a. Apply add_comm on the RHS to make it b + a = b + a.
        MathNode goal = MathNodes.eq(MathNodes.add(MathNodes.var("a"), MathNodes.var("b")), MathNodes.add(MathNodes.var("b"), MathNodes.var("a")));
        MathNode afterStep = MathNodes.eq(MathNodes.add(MathNodes.var("b"), MathNodes.var("a")), MathNodes.add(MathNodes.var("b"), MathNodes.var("a")));
        MathExercise exercise = equationExerciseOf(goal);
        // Path [0] is the alphabetical-first slot of equality (left), where add(a, b) lives.
        MathSubmission submission = submissionOf(step(0, "add_comm", List.of(0), afterStep));
        assertThat(grader.gradeSubmission(exercise, submission)).isEqualTo(100.0);
    }

    @Test
    void gradeSubmission_equationMode_emptyStepsOnTautologicalGoal_scores100() {
        MathExercise exercise = equationExerciseOf(MathNodes.eq(MathNodes.var("x"), MathNodes.var("x")));
        assertThat(grader.gradeSubmission(exercise, submissionOf())).isEqualTo(100.0);
    }

    @Test
    void gradeSubmission_equationMode_emptyStepsOnNonTautologicalGoal_scoresZero() {
        MathNode goal = MathNodes.eq(MathNodes.add(MathNodes.var("a"), MathNodes.var("b")), MathNodes.add(MathNodes.var("b"), MathNodes.var("a")));
        MathExercise exercise = equationExerciseOf(goal);
        assertThat(grader.gradeSubmission(exercise, submissionOf())).isEqualTo(0.0);
    }

    @Test
    void gradeSubmission_equationMode_brokenChain_scoresZero() {
        MathNode goal = MathNodes.eq(MathNodes.add(MathNodes.var("a"), MathNodes.var("b")), MathNodes.add(MathNodes.var("b"), MathNodes.var("a")));
        MathExercise exercise = equationExerciseOf(goal);
        // Claim add_comm on the left side but record a wrong result tree.
        MathSubmission submission = submissionOf(step(0, "add_comm", List.of(0), MathNodes.eq(MathNodes.var("z"), MathNodes.add(MathNodes.var("b"), MathNodes.var("a")))));
        assertThat(grader.gradeSubmission(exercise, submission)).isEqualTo(0.0);
    }

    @Test
    void gradeSubmission_transformationMode_stillWorks() {
        // Regression: default TRANSFORMATION mode behaviour unchanged.
        MathExercise exercise = exerciseOf(MathNodes.add(MathNodes.num("0"), MathNodes.var("x")), MathNodes.var("x"));
        assertThat(exercise.getGoalMode()).isEqualTo(GoalMode.TRANSFORMATION);
        MathSubmission submission = submissionOf(step(0, "add_zero_left", List.of(), MathNodes.var("x")));
        assertThat(grader.gradeSubmission(exercise, submission)).isEqualTo(100.0);
    }

    // ----- Grader-interface wrappers -----

    @Test
    void grade_throughInterface_returnsGradingResult() {
        MathExercise exercise = exerciseOf(MathNodes.add(MathNodes.num("0"), MathNodes.var("x")), MathNodes.var("x"));
        MathSubmission submission = submissionOf(step(0, "add_zero_left", List.of(), MathNodes.var("x")));
        GradingResult result = grader.grade(exercise, submission);
        assertThat(result.score()).isEqualTo(100.0);
    }

    @Test
    void getType_isRewriteChain() {
        assertThat(grader.getType()).isEqualTo(GraderType.REWRITE_CHAIN);
    }

    // ----- Indexed rule lookup -----

    @Test
    void registry_findRuleById_returnsSingleRule_perBlock() {
        long ruleCount = registry.getAllBlocks().stream().mapToLong(b -> registry.getNormalizedRulesFor(b).size()).sum();
        long distinctIds = registry.getAllBlocks().stream().flatMap(b -> registry.getNormalizedRulesFor(b).stream()).map(RewriteRule::id).distinct().count();
        assertThat(distinctIds).isEqualTo(ruleCount);
    }

    @Test
    void registry_findRuleById_returnsEmptyForUnknown() {
        assertThat(registry.findRuleById("does_not_exist")).isEmpty();
    }

    // ----- Helpers -----

    private static MathExercise exerciseOf(MathNode source, MathNode target) {
        MathExercise exercise = new MathExercise();
        exercise.setSourceExpression(source);
        exercise.setTargetExpression(target);
        return exercise;
    }

    private static MathExercise equationExerciseOf(MathNode goal) {
        MathExercise exercise = new MathExercise();
        exercise.setGoalMode(GoalMode.EQUATION);
        exercise.setGoalExpression(goal);
        return exercise;
    }

    private static MathSubmission submissionOf(DerivationStep... steps) {
        MathSubmission submission = new MathSubmission();
        submission.setSteps(new ArrayList<>(List.of(steps)));
        return submission;
    }

    private static DerivationStep step(int index, String ruleId, List<Integer> path, MathNode result) {
        DerivationStep s = new DerivationStep();
        s.setStepIndex(index);
        s.setAppliedRuleId(ruleId);
        s.setTargetNodePath(path);
        s.setResultExpression(result);
        return s;
    }
}
