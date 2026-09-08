/* global Buffer, console, process */
import http from 'node:http';
import { clearTimeout, setTimeout } from 'node:timers';

const port = Number(process.env.HYPERION_LLM_MOCK_PORT ?? 1234);
let requestCount = 0;
let completedHeldProviderResponseCount = 0;
const pendingLateFailures = new Set();
const pendingProviderResponses = new Set();
let holdUnmatchedRequests = false;
const failMarker = 'HYPERION_E2E_FAIL_LLM';
const mechanicalRejectionMarker = 'HYPERION_E2E_MECHANICAL_REJECTION';
const invalidCandidatePath = 'solution/src/de/test/VerifierRejected.java';
const diagnosticFilePath = 'solution/src/de/test/HyperionDiagnostic.java';
const writeSnapshotMarker = 'HYPERION_E2E_WRITE_SNAPSHOT';
const submitSeedMarker = 'HYPERION_E2E_SUBMIT_SEEDED_EXERCISE';
const reviewRequiredMarker = 'HYPERION_E2E_REQUIRE_INSTRUCTOR_REVIEW';
const submitNewExerciseMarker = 'HYPERION_E2E_SUBMIT_NEW_EXERCISE';
const correctedSeedStatementMarker = 'more than 5 dates';
const adaptedPolicyMarker = 'DATES_SIZE_THRESHOLD = 5';
const adaptedTestLoopMarker = 'for (int i = 0; i < 6; i++)';
const adaptedTestMessageMarker = 'The sort algorithm of Context was not MergeSort for a list with more than 5 dates.';
const adaptedBoundaryTestLoopMarker = 'for (int i = 0; i < 5; i++)';
const adaptedBoundaryTestMessageMarker = 'The sort algorithm of Context was not BubbleSort for a list with less or equal than 5 dates.';
const criticPromptMarker = 'reviewer for a generated programming exercise';
const specificationReviewPromptMarker = 'review one candidate programming-exercise specification';
const conceptGenerationPromptMarker = 'Generate exactly three candidate realizations for the instructor brief';
const conceptReviewPromptMarker = 'Review exactly three generator-authored exercise concepts';
const conceptAdmissionPromptMarker = 'Audit one already-selected exercise concept';
const semanticMutantPromptMarker = 'independent semantic mutation-test author';
const contractWitnessPromptMarker = 'author executable witnesses for a generated programming exercise';
const oracleReviewPromptMarker = 'test-oracle reviewer for a generated programming exercise';
const draftPromptMarker = 'expert technical writing assistant for programming exercise problem statements';
const draftProblemStatement = `# Temperature Alert Classification

## Introduction

Create a small Java program that classifies temperature readings. The exercise focuses on precise boundary handling and predictable validation behavior.

## Required Behaviors

- Classify readings below 0 as \`FREEZING\`, readings from 0 through 25 as \`NORMAL\`, and readings above 25 as \`HOT\`.
- Preserve the input order when classifying multiple readings.
- Reject a collection containing a missing reading. If validation fails, produce no partial result.

## Boundary Cases

- An empty collection produces an empty result.
- The exact boundary values 0 and 25 are classified as \`NORMAL\`.

## Worked Examples

- \`[-1, 0, 26]\` produces \`[FREEZING, NORMAL, HOT]\`.
- \`[]\` produces \`[]\`.
`;
const generatedProblemStatement = `# Temperature Alert Classification

Implement \`TemperatureClassifier.classify(List<Integer>)\`. Return one label for each reading in input order: \`FREEZING\` below 0, \`NORMAL\` from 0 through 25, and \`HOT\` above 25. Reject null readings without returning a partial result. An empty list produces an empty list.

## Tasks

### 1. Classify representative temperatures
[task][Classify temperatures](testRepresentativeTemperatures)

Implement the three classification ranges and preserve the order of all readings.

### 2. Handle boundaries and empty input
[task][Handle boundaries and empty input](testBoundariesAndEmptyInput)

Treat 0 and 25 as inclusive NORMAL boundaries, and return an empty result for an empty input.

### 3. Reject missing readings atomically
[task][Reject missing readings atomically](testMissingReadingIsRejected)

Validate the complete input before returning classifications so a null reading cannot produce a partial result.
`;
const correctedGeneratedProblemStatement = generatedProblemStatement;
const generatedSpec = `# Temperature Alert Classification

## Rules
- R1: readings below 0 are FREEZING, readings from 0 through 25 are NORMAL, and readings above 25 are HOT.
- R2: classifications preserve input order.
- R3: a null reading rejects the complete input without a partial result.
- R4: an empty input produces an empty result.

## Worked Examples
| Rules | Input | Expected |
|-------|-------|----------|
| R1, R2 | -1, 0, 26 | FREEZING, NORMAL, HOT |
| R3, R4 | null reading / empty list | exception / empty list |

## Design
| Type | Role | Template status |
|------|------|-----------------|
| TemperatureClassifier | classifies an ordered list of readings | stubbed |

## Public API
\`\`\`java
public final class TemperatureClassifier { public static java.util.List<String> classify(java.util.List<Integer> readings); }
\`\`\`

## Testing Strategy
| Seam | Owner type | Observable responsibility | Weight | Hidden variant |
|------|------------|---------------------------|--------|----------------|
| S1 | TemperatureClassifier | representative values and order | 3 | no |
| S2 | TemperatureClassifier | exact boundaries and empty input | 3 | no |
| S3 | TemperatureClassifier | atomic null rejection | 3 | no |

## Contract Risk Inventory
| Seam | Rules | Admitted partitions | Excluded inputs |
|------|-------|---------------------|-----------------|
| S1 | R1, R2 | S1.P1: below, within, and above boundaries | none |
| S2 | R1, R4 | S2.P1: exact 0 and 25; S2.P2: empty input | none |
| S3 | R3 | S3.P1: null before and after valid readings | null list |

## Diagram
no — single-class exercise
`;
const generatedConcepts = `## Candidate 1
Domain situation: A monitoring console categorizes ordered temperature readings into alert bands.
Real constraint: Operators need consistent inclusive boundary handling while scanning a small batch.
Common caller goal: Obtain one meaningful alert category for every reading in its original order.
Student-owned objective: Implement the complete comparison and branching behavior that assigns each reading to its category.
Student-owned reasoning: Translate an exhaustive ordered numeric partition into correct branching while preserving collection order.
Alternative policies: Not applicable
Observable substitution: Not applicable
Likely supplied support: The input collection and routine project infrastructure.

## Candidate 2
Domain situation: A laboratory dashboard groups ordered sensor readings into operating ranges.
Real constraint: Technicians must see deterministic classifications at every shared range boundary.
Common caller goal: Classify a short ordered sample without losing its correspondence to the inputs.
Student-owned objective: Implement the range comparisons and ordered transformation for all admitted readings.
Student-owned reasoning: Reconcile inclusive and exclusive range edges and produce one result per input.
Alternative policies: Not applicable
Observable substitution: Not applicable
Likely supplied support: Basic data setup and the build harness.

## Candidate 3
Domain situation: A field station summarizes ordered numeric measurements into response levels.
Real constraint: Downstream staff rely on unambiguous treatment of measurements exactly on a threshold.
Common caller goal: Turn a small sequence of measurements into an equally ordered sequence of levels.
Student-owned objective: Implement exhaustive branching across the measurement ranges and preserve sequence order.
Student-owned reasoning: Design control flow that covers every range exactly once and maps all inputs without reordering.
Alternative policies: Not applicable
Observable substitution: Not applicable
Likely supplied support: Measurement values and routine scaffolding.
`;
const generatedTestPlan = `{"tests":[
  {"name":"testRepresentativeTemperatures","seam":"S1","riskPartitions":["S1.P1"],"seamWeightTier":3,"visibility":"ALWAYS"},
  {"name":"testBoundariesAndEmptyInput","seam":"S2","riskPartitions":["S2.P1","S2.P2"],"seamWeightTier":3,"visibility":"ALWAYS"},
  {"name":"testMissingReadingIsRejected","seam":"S3","riskPartitions":["S3.P1"],"seamWeightTier":3,"visibility":"ALWAYS"}
]}`;
const generatedSolution = `package de.tum.cit.aet.temperaturealert;

import java.util.ArrayList;
import java.util.List;

public final class TemperatureClassifier {
    private TemperatureClassifier() {
    }

    public static List<String> classify(List<Integer> readings) {
        List<String> labels = new ArrayList<>();
        for (Integer reading : readings) {
            if (reading == null) {
                throw new IllegalArgumentException("readings must not contain null");
            }
            labels.add(reading < 0 ? "FREEZING" : reading <= 25 ? "NORMAL" : "HOT");
        }
        return labels;
    }
}
`;
const generatedTemplate = `package de.tum.cit.aet.temperaturealert;

import java.util.List;

public final class TemperatureClassifier {
    private TemperatureClassifier() {
    }

    public static List<String> classify(List<Integer> readings) {
        // TODO S1: Classify representative values while preserving input order.
        // TODO S2: Handle the exact boundaries and empty input.
        // TODO S3: Reject an input containing a null reading atomically.
        throw new UnsupportedOperationException("Not implemented");
    }
}
`;
const generatedTests = `package de.tum.cit.aet.temperaturealert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.in.test.api.BlacklistPath;
import de.tum.in.test.api.StrictTimeout;
import de.tum.in.test.api.WhitelistPath;
import de.tum.in.test.api.jupiter.Public;

@Public
@WhitelistPath("target")
@BlacklistPath("target/test-classes")
class TemperatureClassifierTest {
    @Test
    @StrictTimeout(1)
    void testRepresentativeTemperatures() {
        assertEquals(List.of("FREEZING", "NORMAL", "HOT"), TemperatureClassifier.classify(List.of(-4, 12, 30)), "Each reading should receive its matching label");
    }

    @Test
    @StrictTimeout(1)
    void testBoundariesAndEmptyInput() {
        assertEquals(List.of("NORMAL", "NORMAL"), TemperatureClassifier.classify(List.of(0, 25)), "Both inclusive boundary values should be normal");
        assertEquals(List.of(), TemperatureClassifier.classify(List.of()), "An empty input should produce an empty result");
    }

    @Test
    @StrictTimeout(1)
    void testMissingReadingIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> TemperatureClassifier.classify(Arrays.asList(-1, null, 26)), "A null reading should reject the complete input");
    }
}
`;
const correctedSeedProblemStatement = `In this exercise, we want to implement sorting algorithms and choose them based on runtime specific variables.

### Part 1: Sorting

First, we need to implement two sorting algorithms, in this case \`MergeSort\` and \`BubbleSort\`.

**You have the following tasks:**

1. [task][Implement Bubble Sort](testBubbleSort,testClass[BubbleSort])
Implement the method \`performSort(List<Date>)\` in the class \`BubbleSort\`. Make sure to follow the Bubble Sort algorithm exactly.

2. [task][Implement Merge Sort](testMergeSort,testClass[MergeSort])
Implement the method \`performSort(List<Date>)\` in the class \`MergeSort\`. Make sure to follow the Merge Sort algorithm exactly.

### Part 2: Strategy Pattern

We want the application to apply different algorithms for sorting a \`List\` of \`Date\` objects.
Use the strategy pattern to select the right sorting algorithm at runtime.

**You have the following tasks:**

1. [task][SortStrategy Interface](testClass[SortStrategy],testMethods[SortStrategy])
Create a \`SortStrategy\` interface and adjust the sorting algorithms so that they implement this interface.

2. [task][Context Class](testAttributes[Context],testMethods[Context])
Create and implement a \`Context\` class following the below class diagram.

3. [task][Context Policy](testConstructors[Policy],testAttributes[Policy],testMethods[Policy])
Create and implement a \`Policy\` class following the below class diagram with a simple configuration mechanism:

    1. [task][Use merge sort for big lists](testUseMergeSortForBigList)
    Select \`MergeSort\` when the List has more than 5 dates.

    2. [task][Use bubble sort for small lists](testUseBubbleSortForSmallList)
    Select \`BubbleSort\` when the List has less or equal 5 dates.

4. Complete the \`Client\` class which demonstrates switching between two strategies at runtime.
`;

function hasAcknowledgedToolCall(rawBody, toolName, expectedArguments) {
    try {
        const messages = JSON.parse(rawBody).messages;
        return (
            Array.isArray(messages) &&
            messages.some((message, messageIndex) =>
                message?.tool_calls?.some((toolCall) => {
                    if (toolCall?.function?.name !== toolName || typeof toolCall.function.arguments !== 'string') {
                        return false;
                    }
                    try {
                        return (
                            expectedArguments(JSON.parse(toolCall.function.arguments)) &&
                            typeof toolCall.id === 'string' &&
                            messages.slice(messageIndex + 1).some((candidate) => candidate?.role === 'tool' && candidate.tool_call_id === toolCall.id)
                        );
                    } catch {
                        return false;
                    }
                }),
            )
        );
    } catch {
        return false;
    }
}

function currentCandidateContains(rawBody, expectedContent) {
    const artifactsStart = rawBody.indexOf('MECHANICALLY VERIFIED CANDIDATE ARTIFACTS:');
    if (artifactsStart < 0) {
        return false;
    }
    const adaptationChangesStart = rawBody.indexOf('ADAPTATION CHANGES (baseline to candidate):', artifactsStart);
    const artifactsEnd = adaptationChangesStart < 0 ? rawBody.length : adaptationChangesStart;
    return rawBody.slice(artifactsStart, artifactsEnd).includes(expectedContent);
}

function jsonResponse(res, status, body) {
    if (res.destroyed || res.writableEnded) {
        return;
    }
    res.writeHead(status, { 'content-type': 'application/json' });
    res.end(JSON.stringify(body));
}

function failPendingResponses(pendingResponses, message) {
    let released = 0;
    for (const pendingResponse of pendingResponses) {
        pendingResponses.delete(pendingResponse);
        jsonResponse(pendingResponse, 400, { error: { message } });
        released++;
    }
    return released;
}

function resetScenario() {
    const releasedLateFailures = failPendingResponses(pendingLateFailures, 'Hyperion E2E scenario reset released a pending failure response');
    const releasedProviderResponses = failPendingResponses(pendingProviderResponses, 'Hyperion E2E scenario reset released a held provider response');
    requestCount = 0;
    completedHeldProviderResponseCount = 0;
    holdUnmatchedRequests = false;
    return { releasedLateFailures, releasedProviderResponses };
}

function toolCallResponse(requestNumber, toolName, args) {
    return {
        id: `chatcmpl-hyperion-e2e-${requestNumber}`,
        object: 'chat.completion',
        created: Math.floor(Date.now() / 1000),
        model: 'hyperion-e2e-mock',
        choices: [
            {
                index: 0,
                message: {
                    role: 'assistant',
                    content: '',
                    tool_calls: [
                        {
                            id: `call_hyperion_e2e_${requestNumber}`,
                            type: 'function',
                            function: {
                                name: toolName,
                                arguments: JSON.stringify(args),
                            },
                        },
                    ],
                },
                finish_reason: 'tool_calls',
            },
        ],
        usage: { prompt_tokens: 1, completion_tokens: 1, total_tokens: 2, prompt_tokens_details: { cached_tokens: 1 } },
    };
}

function textResponse(requestNumber, content) {
    return {
        id: `chatcmpl-hyperion-e2e-${requestNumber}`,
        object: 'chat.completion',
        created: Math.floor(Date.now() / 1000),
        model: 'hyperion-e2e-mock',
        choices: [{ index: 0, message: { role: 'assistant', content }, finish_reason: 'stop' }],
        usage: { prompt_tokens: 1, completion_tokens: 1, total_tokens: 2, prompt_tokens_details: { cached_tokens: 1 } },
    };
}

const server = http.createServer((req, res) => {
    if (req.method === 'GET' && req.url === '/health') {
        jsonResponse(res, 200, {
            ok: true,
            requestCount,
            completedHeldProviderResponseCount,
            pendingLateFailureCount: pendingLateFailures.size,
            pendingProviderResponseCount: pendingProviderResponses.size,
            holdUnmatchedRequests,
        });
        return;
    }

    if (req.method === 'POST' && req.url === '/release-late-failure') {
        if (pendingLateFailures.size !== 1) {
            jsonResponse(res, 409, { error: { message: `Expected one pending late failure, found ${pendingLateFailures.size}` } });
            return;
        }
        failPendingResponses(pendingLateFailures, 'Hyperion E2E requested LLM failure after producing diagnostic work');
        jsonResponse(res, 200, { released: 1 });
        return;
    }

    if (req.method === 'POST' && req.url === '/release-held-provider-response') {
        if (pendingProviderResponses.size !== 1) {
            jsonResponse(res, 409, { error: { message: `Expected one held provider response, found ${pendingProviderResponses.size}` } });
            return;
        }
        for (const pendingResponse of pendingProviderResponses) {
            pendingProviderResponses.delete(pendingResponse);
            let settled = false;
            const timeout = setTimeout(() => settle(504, 'Timed out while releasing the held provider response'), 5_000);
            const settle = (status, error) => {
                if (settled) {
                    return;
                }
                settled = true;
                clearTimeout(timeout);
                jsonResponse(res, status, error ? { error: { message: error } } : { released: 1 });
            };
            pendingResponse.once('finish', () => {
                completedHeldProviderResponseCount++;
                settle(200);
            });
            pendingResponse.once('close', () => settle(502, 'Held provider response closed before completion'));
            pendingResponse.once('error', () => settle(502, 'Held provider response failed before completion'));
            jsonResponse(
                pendingResponse,
                200,
                toolCallResponse(requestCount, 'write_file', {
                    path: 'solution/src/de/test/LateAfterCancellation.java',
                    content: 'package de.test;\n\nfinal class LateAfterCancellation {}\n',
                }),
            );
        }
        return;
    }

    if (req.method === 'POST' && req.url === '/reset') {
        jsonResponse(res, 200, resetScenario());
        return;
    }

    if (req.method === 'POST' && req.url === '/scenario') {
        const chunks = [];
        req.on('data', (chunk) => chunks.push(chunk));
        req.on('end', () => {
            try {
                const scenario = JSON.parse(Buffer.concat(chunks).toString('utf8'));
                if (typeof scenario.holdUnmatchedRequests !== 'boolean') {
                    jsonResponse(res, 400, { error: { message: 'holdUnmatchedRequests must be a boolean' } });
                    return;
                }
                holdUnmatchedRequests = scenario.holdUnmatchedRequests;
                jsonResponse(res, 200, { holdUnmatchedRequests });
            } catch {
                jsonResponse(res, 400, { error: { message: 'Scenario body must be valid JSON' } });
            }
        });
        return;
    }

    if (req.method === 'POST' && req.url === '/v1/chat/completions') {
        const chunks = [];
        req.on('data', (chunk) => chunks.push(chunk));
        req.on('end', () => {
            requestCount++;
            const requestNumber = requestCount;
            const body = Buffer.concat(chunks).toString('utf8');
            if (body.includes(draftPromptMarker)) {
                if (!body.includes(submitNewExerciseMarker)) {
                    jsonResponse(res, 400, { error: { message: 'The instructor brief was not forwarded to draft generation' } });
                    return;
                }
                jsonResponse(res, 200, textResponse(requestNumber, draftProblemStatement));
                return;
            }
            if (body.includes(failMarker)) {
                if (!hasAcknowledgedToolCall(body, 'write_file', (args) => args.path === diagnosticFilePath)) {
                    jsonResponse(
                        res,
                        200,
                        toolCallResponse(requestNumber, 'write_file', {
                            path: diagnosticFilePath,
                            content:
                                'package de.test;\n\npublic class HyperionDiagnostic {\n    public String marker() {\n        return "captured-after-provider-failure";\n    }\n}\n',
                        }),
                    );
                    return;
                }
                pendingLateFailures.add(res);
                res.once('close', () => pendingLateFailures.delete(res));
                return;
            }
            if (body.includes(mechanicalRejectionMarker)) {
                if (!hasAcknowledgedToolCall(body, 'write_file', (args) => args.path === invalidCandidatePath)) {
                    jsonResponse(
                        res,
                        200,
                        toolCallResponse(requestNumber, 'write_file', {
                            path: invalidCandidatePath,
                            content: 'package de.test;\n\npublic class VerifierRejected {\n    this is not valid Java\n}\n',
                        }),
                    );
                    return;
                }
                jsonResponse(res, 200, toolCallResponse(requestNumber, 'submit', { summary: 'Submit an intentionally invalid candidate to exercise the real verifier.' }));
                return;
            }
            if (body.includes(conceptGenerationPromptMarker)) {
                jsonResponse(res, 200, textResponse(requestNumber, generatedConcepts));
                return;
            }
            if (body.includes(conceptReviewPromptMarker)) {
                const evaluation = (candidate) => ({
                    candidate,
                    candidateEvidenceIds: [`C${candidate}.2`, `C${candidate}.5`, `C${candidate}.6`],
                    briefCoverage: 'The concept preserves the introductory Java branching, comparison, ordered readings, and explicit-boundary objective.',
                    objectiveCounterfactual: 'Without the learner-owned range decisions, the exercise no longer practices the requested comparison and branching objective.',
                    difficultyFit: 'The concise branching and ordered transformation are proportionate to an introductory exercise.',
                    smallestStudentImplementation: 'A complete classifier must compare each reading against exhaustive range edges and emit one ordered category.',
                    reasoningAfterRoutineWork: 'Students still determine exhaustive branch control flow, inclusive edge placement, and order-preserving traversal.',
                    domainGrounding: 'Operational reading categories make exact thresholds and consistent ordered classification meaningful.',
                    feasibility: 'The behavior fits one small Java class and can be observed directly through representative and boundary inputs.',
                    briefCovered: true,
                    objectiveEssential: true,
                    learningFitSufficient: true,
                    learnerOwnsObjectiveMechanism: true,
                    objectiveObservable: true,
                    prematureContractClosure: false,
                    difficultySufficient: true,
                    domainGrounded: true,
                    feasibleAndProportionate: true,
                });
                jsonResponse(
                    res,
                    200,
                    textResponse(
                        requestNumber,
                        JSON.stringify({
                            selectedCandidate: 1,
                            selectionReason: 'Candidate 1 most directly connects ordered temperature monitoring to exhaustive student-owned branching while remaining introductory.',
                            evaluations: [evaluation(1), evaluation(2), evaluation(3)],
                        }),
                    ),
                );
                return;
            }
            if (body.includes(conceptAdmissionPromptMarker)) {
                jsonResponse(
                    res,
                    200,
                    textResponse(
                        requestNumber,
                        JSON.stringify({
                            auditedCandidateEvidenceIds: ['C1.2', 'C1.5', 'C1.6'],
                            smallestEquivalentImplementation: 'The minimal implementation compares each ordered reading against a complete range partition and returns one corresponding category.',
                            observablePartitionAudit: 'Each qualitative range changes the returned category, while order preservation remains observable across a multi-reading input.',
                            unsupportedChoices: [],
                            unobservableRequirements: [],
                            redundantDistinctions: [],
                            admissible: true,
                            summary: 'The concept stays qualitative, observable, proportionate, and free of unsupported implementation mandates.',
                        }),
                    ),
                );
                return;
            }
            if (body.includes(semanticMutantPromptMarker)) {
                jsonResponse(res, 200, textResponse(requestNumber, '{"mutants":[]}'));
                return;
            }
            if (body.includes(contractWitnessPromptMarker)) {
                jsonResponse(res, 200, textResponse(requestNumber, '{"witnesses":[]}'));
                return;
            }
            if (body.includes(specificationReviewPromptMarker)) {
                jsonResponse(
                    res,
                    200,
                    textResponse(
                        requestNumber,
                        JSON.stringify({
                            learningFit: {
                                briefEvidenceIds: ['B2'],
                                specEvidenceIds: ['E3', 'E15', 'E18', 'E23', 'E24', 'E25'],
                                objectiveEvidenceIds: ['E3', 'E18'],
                                studentOwnershipEvidenceIds: ['E15'],
                                assessmentEvidenceIds: ['E23', 'E24', 'E25'],
                                objectiveMechanism: 'Students implement the classifier that compares each reading against both inclusive category boundaries and preserves input order.',
                                remainingStudentReasoning: 'Students must translate the complete ordered numeric partition into branching logic, preserve collection order, and validate the whole input before producing a result.',
                                domainGrounding: 'The classification rules model temperature alert ranges, so every comparison and boundary decision directly determines a domain label.',
                                learnerOwnsObjectiveMechanism: true,
                                objectiveObservable: true,
                                difficultySufficient: true,
                                domainGrounded: true,
                                sufficient: true,
                                direction: 'SUFFICIENT',
                            },
                            conceptAlignment: {
                                briefEvidenceIds: ['B2'],
                                conceptEvidenceIds: ['C2', 'C5', 'C6'],
                                specEvidenceIds: ['E3', 'E15', 'E18'],
                                disposition: 'ALIGNED',
                                reason: 'The specification preserves the selected monitoring classification interaction and leaves students responsible for its complete branching mechanism.',
                            },
                            boundaryChecks: [
                                {
                                    briefEvidenceIds: ['B2'],
                                    specEvidenceIds: ['E3', 'E18', 'E24'],
                                    publicSetup: 'Call TemperatureClassifier.classify with readings containing 0 and 25.',
                                    observedOperation: 'TemperatureClassifier.classify returns NORMAL for both exact boundary readings.',
                                    reachable: true,
                                    timingPreserved: true,
                                    reason: 'The public list input admits both exact integers and R1 assigns each to the inclusive NORMAL range.',
                                },
                                {
                                    briefEvidenceIds: ['B2'],
                                    specEvidenceIds: ['E5', 'E18', 'E25'],
                                    publicSetup: 'Call TemperatureClassifier.classify with a list containing a null reading.',
                                    observedOperation: 'TemperatureClassifier.classify rejects the complete input without returning a partial result.',
                                    reachable: true,
                                    timingPreserved: true,
                                    reason: 'The public list input can contain null and R3 makes rejection observable during the classification call.',
                                },
                            ],
                            priorFindingChecks: [],
                            exampleChecks: [
                                {
                                    exampleEvidenceId: 'E10',
                                    replayedOutcome: 'FREEZING, NORMAL, HOT in the original order.',
                                    consistent: true,
                                    reason: '-1 is below 0, 0 is inside the inclusive range, and 26 is above 25.',
                                },
                                {
                                    exampleEvidenceId: 'E11',
                                    replayedOutcome: 'A null reading causes rejection, while an empty list returns an empty list.',
                                    consistent: true,
                                    reason: 'The two outcomes follow directly from R3 and R4 without conflicting state or preconditions.',
                                },
                            ],
                            omissions: [],
                            conflicts: [],
                            internalConflicts: [],
                            ambiguities: [],
                            unsupportedConstraints: [],
                        }),
                    ),
                );
                return;
            }
            if (body.includes(criticPromptMarker)) {
                const weakThresholdOracle = body.includes(submitSeedMarker) && currentCandidateContains(body, 'for (int i = 0; i < 3; i++)');
                const oracleReview = body.includes(oracleReviewPromptMarker);
                if (body.includes(reviewRequiredMarker) && !oracleReview) {
                    jsonResponse(res, 200, textResponse(requestNumber, '{}'));
                    return;
                }
                const audit = oracleReview
                    ? `"exampleChecks":[],"apiChecks":[],"templateChecks":[],"mutantChecks":[{"mutant":"a threshold of 4","killed":${!weakThresholdOracle},"sourceQuote":"use merge sort for lists with more than 5 dates and update the matching test.","reason":"the boundary tests must distinguish sizes 5 and 6"}]`
                    : '"exampleChecks":[],"apiChecks":[{"symbol":"seeded public API","discoverable":true,"reason":"the statement and starter expose it"}],"templateChecks":[{"test":"seeded task groups","targetReached":true,"reason":"the existing starter reaches each target"}],"mutantChecks":[]';
                jsonResponse(
                    res,
                    200,
                    textResponse(
                        requestNumber,
                        `{${audit},"uncovered":[],"contradictions":[],"hiddenRequirements":[],"weakOracle":[],"templateGaps":[],"missingExamples":[],"invented":[],"unrequestedChanges":[],"missingRequestedChanges":[]}`,
                    ),
                );
                return;
            }
            if (body.includes(submitNewExerciseMarker) || body.includes('# Temperature Alert Classification')) {
                const specStage = body.includes('SPEC.md: the only writable artifact in this stage');
                const testsStage = body.includes('solution/, template/, tests/, and test-plan.json');
                const statementStage = body.includes('problem-statement.md: the only writable artifact in this stage');
                if (specStage) {
                    if (!hasAcknowledgedToolCall(body, 'write_file', (args) => args.path === 'SPEC.md')) {
                        jsonResponse(res, 200, toolCallResponse(requestNumber, 'write_file', { path: 'SPEC.md', content: generatedSpec }));
                        return;
                    }
                    jsonResponse(res, 200, toolCallResponse(requestNumber, 'submit', { summary: 'Submitted the deterministic temperature specification.' }));
                    return;
                }
                if (testsStage) {
                    const generatedPackage = body.match(/Module \/ package name: ([a-zA-Z0-9_.]+)/)?.[1] ?? 'de.tum.cit.aet.temperaturealert';
                    const generatedPackagePath = generatedPackage.replaceAll('.', '/');
                    const withGeneratedPackage = (content) => content.replaceAll('de.tum.cit.aet.temperaturealert', generatedPackage);
                    const files = [
                        [`solution/src/${generatedPackagePath}/TemperatureClassifier.java`, withGeneratedPackage(generatedSolution)],
                        [`template/src/${generatedPackagePath}/TemperatureClassifier.java`, withGeneratedPackage(generatedTemplate)],
                        [`tests/test/${generatedPackagePath}/TemperatureClassifierTest.java`, withGeneratedPackage(generatedTests)],
                        ['test-plan.json', generatedTestPlan],
                    ];
                    const nextFile = files.find(([file]) => !hasAcknowledgedToolCall(body, 'write_file', (args) => args.path === file));
                    if (nextFile) {
                        jsonResponse(res, 200, toolCallResponse(requestNumber, 'write_file', { path: nextFile[0], content: nextFile[1] }));
                        return;
                    }
                    jsonResponse(res, 200, toolCallResponse(requestNumber, 'submit', { summary: 'Submitted deterministic temperature code and tests.' }));
                    return;
                }
                if (
                    statementStage &&
                    body.includes('These [task] bindings reference names that match no actual test') &&
                    !hasAcknowledgedToolCall(
                        body,
                        'write_file',
                        (args) => args.path === 'problem-statement.md' && args.content?.includes('### 1. Classify representative temperatures'),
                    )
                ) {
                    jsonResponse(res, 200, toolCallResponse(requestNumber, 'write_file', { path: 'problem-statement.md', content: correctedGeneratedProblemStatement }));
                    return;
                }
                if (statementStage) {
                    if (!hasAcknowledgedToolCall(body, 'write_file', (args) => args.path === 'problem-statement.md')) {
                        jsonResponse(res, 200, toolCallResponse(requestNumber, 'write_file', { path: 'problem-statement.md', content: generatedProblemStatement }));
                        return;
                    }
                    jsonResponse(res, 200, toolCallResponse(requestNumber, 'submit', { summary: 'Submitted the deterministic temperature problem statement.' }));
                    return;
                }
            }
            if (body.includes(writeSnapshotMarker) && !body.includes('HyperionPreview.java')) {
                jsonResponse(
                    res,
                    200,
                    toolCallResponse(requestNumber, 'write_file', {
                        path: 'solution/src/de/test/HyperionPreview.java',
                        content: 'package de.test;\n\npublic class HyperionPreview {\n    public String marker() {\n        return "retained-preview";\n    }\n}\n',
                    }),
                );
                return;
            }
            if (
                body.includes(submitSeedMarker) &&
                body.includes('threshold of 4') &&
                !hasAcknowledgedToolCall(
                    body,
                    'edit_file',
                    (args) => args.path === 'tests/test/de/test/SortingExampleBehaviorTest.java' && args.newText === adaptedBoundaryTestLoopMarker,
                )
            ) {
                jsonResponse(
                    res,
                    200,
                    toolCallResponse(requestNumber, 'edit_file', {
                        path: 'tests/test/de/test/SortingExampleBehaviorTest.java',
                        oldText: 'for (int i = 0; i < 3; i++)',
                        newText: adaptedBoundaryTestLoopMarker,
                    }),
                );
                return;
            }
            if (
                body.includes(submitSeedMarker) &&
                body.includes('threshold of 4') &&
                !hasAcknowledgedToolCall(
                    body,
                    'edit_file',
                    (args) => args.path === 'tests/test/de/test/SortingExampleBehaviorTest.java' && args.newText === adaptedBoundaryTestMessageMarker,
                )
            ) {
                jsonResponse(
                    res,
                    200,
                    toolCallResponse(requestNumber, 'edit_file', {
                        path: 'tests/test/de/test/SortingExampleBehaviorTest.java',
                        oldText: 'The sort algorithm of Context was not BubbleSort for a list with less or equal than 10 dates.',
                        newText: adaptedBoundaryTestMessageMarker,
                    }),
                );
                return;
            }
            if (
                body.includes(submitSeedMarker) &&
                !hasAcknowledgedToolCall(body, 'write_file', (args) => args.path === 'problem-statement.md' && args.content?.includes(correctedSeedStatementMarker))
            ) {
                jsonResponse(res, 200, toolCallResponse(requestNumber, 'write_file', { path: 'problem-statement.md', content: correctedSeedProblemStatement }));
                return;
            }
            if (
                body.includes(submitSeedMarker) &&
                !hasAcknowledgedToolCall(body, 'edit_file', (args) => args.path === 'solution/src/de/test/Policy.java' && args.newText === adaptedPolicyMarker)
            ) {
                jsonResponse(
                    res,
                    200,
                    toolCallResponse(requestNumber, 'edit_file', {
                        path: 'solution/src/de/test/Policy.java',
                        oldText: 'DATES_SIZE_THRESHOLD = 10',
                        newText: adaptedPolicyMarker,
                    }),
                );
                return;
            }
            if (
                body.includes(submitSeedMarker) &&
                !hasAcknowledgedToolCall(body, 'edit_file', (args) => args.path === 'tests/test/de/test/SortingExampleBehaviorTest.java' && args.newText === adaptedTestLoopMarker)
            ) {
                jsonResponse(
                    res,
                    200,
                    toolCallResponse(requestNumber, 'edit_file', {
                        path: 'tests/test/de/test/SortingExampleBehaviorTest.java',
                        oldText: 'for (int i = 0; i < 11; i++)',
                        newText: adaptedTestLoopMarker,
                    }),
                );
                return;
            }
            if (
                body.includes(submitSeedMarker) &&
                !hasAcknowledgedToolCall(
                    body,
                    'edit_file',
                    (args) => args.path === 'tests/test/de/test/SortingExampleBehaviorTest.java' && args.newText === adaptedTestMessageMarker,
                )
            ) {
                jsonResponse(
                    res,
                    200,
                    toolCallResponse(requestNumber, 'edit_file', {
                        path: 'tests/test/de/test/SortingExampleBehaviorTest.java',
                        oldText: 'The sort algorithm of Context was not MergeSort for a list with more than 10 dates.',
                        newText: adaptedTestMessageMarker,
                    }),
                );
                return;
            }
            if (body.includes(submitSeedMarker)) {
                jsonResponse(res, 200, toolCallResponse(requestNumber, 'submit', { summary: 'Submitted the seeded Java exercise for deterministic E2E verification.' }));
                return;
            }
            if (holdUnmatchedRequests) {
                pendingProviderResponses.add(res);
                res.once('close', () => pendingProviderResponses.delete(res));
                return;
            }
            jsonResponse(res, 400, {
                error: { message: 'Unhandled Hyperion E2E LLM request. Configure holdUnmatchedRequests only for scenarios that intentionally cancel a running provider call.' },
            });
        });
        return;
    }

    jsonResponse(res, 404, { error: `Unhandled ${req.method} ${req.url}` });
});

server.listen(port, '127.0.0.1', () => {
    console.log(`Hyperion E2E LLM mock listening on http://127.0.0.1:${port}`);
});

function shutDown() {
    failPendingResponses(pendingLateFailures, 'Hyperion E2E mock is shutting down');
    failPendingResponses(pendingProviderResponses, 'Hyperion E2E mock is shutting down');
    server.close(() => process.exit(0));
}

process.on('SIGTERM', shutDown);
process.on('SIGINT', shutDown);
