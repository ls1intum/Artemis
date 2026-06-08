import { describe, expect, it } from 'vitest';

import { BUILD_PHASE_NAME_PATTERN, BUILD_PHASE_RESERVED_NAMES, hasExpectedTestsBeforeDueDate, parseBuildPlanPhases } from './build-plan-phases.model';

describe('build-plan-phases.model', () => {
    it('parses a valid build plan phases json', () => {
        const parsed = parseBuildPlanPhases(
            JSON.stringify({
                phases: [
                    {
                        name: 'compile',
                        script: './gradlew compileTestJava',
                        condition: 'ALWAYS',
                        forceRun: false,
                        resultPaths: ['build/test-results/**/*.xml'],
                    },
                ],
                dockerImage: 'gradle:8.10.2-jdk21',
            }),
        );

        expect(parsed).toEqual({
            phases: [
                {
                    name: 'compile',
                    script: './gradlew compileTestJava',
                    condition: 'ALWAYS',
                    forceRun: false,
                    resultPaths: ['build/test-results/**/*.xml'],
                },
            ],
            dockerImage: 'gradle:8.10.2-jdk21',
        });
    });

    it('returns undefined for invalid json and undefined input', () => {
        expect(parseBuildPlanPhases(undefined)).toBeUndefined();
        expect(parseBuildPlanPhases('{ invalid-json')).toBeUndefined();
    });

    it('returns undefined when phases payload is malformed', () => {
        expect(parseBuildPlanPhases(JSON.stringify({ phases: 'not-an-array' }))).toBeUndefined();

        expect(
            parseBuildPlanPhases(
                JSON.stringify({
                    phases: [
                        {
                            name: 'test',
                            script: './gradlew test',
                            condition: 'SOMETIMES',
                            forceRun: false,
                            resultPaths: [],
                        },
                    ],
                }),
            ),
        ).toBeUndefined();

        expect(
            parseBuildPlanPhases(
                JSON.stringify({
                    phases: [
                        {
                            name: 'test',
                            script: './gradlew test',
                            condition: 'ALWAYS',
                            forceRun: false,
                            resultPaths: [123],
                        },
                    ],
                }),
            ),
        ).toBeUndefined();
    });

    it('validates phase naming constraints used by the editor', () => {
        expect(BUILD_PHASE_NAME_PATTERN.test('compile_phase_1')).toBe(true);
        expect(BUILD_PHASE_NAME_PATTERN.test('1compile')).toBe(false);
        expect(BUILD_PHASE_NAME_PATTERN.test('compile-phase')).toBe(false);

        expect(BUILD_PHASE_RESERVED_NAMES.has('main')).toBe(true);
        expect(BUILD_PHASE_RESERVED_NAMES.has('final_force_run_post_action')).toBe(true);
    });

    it('defaults undefined resultPaths to an empty array', () => {
        const parsed = parseBuildPlanPhases(
            JSON.stringify({
                phases: [
                    {
                        name: 'test',
                        script: './gradlew test',
                        condition: 'ALWAYS',
                        forceRun: false,
                        // resultPaths intentionally omitted
                    },
                ],
            }),
        );
        expect(parsed?.phases[0].resultPaths).toEqual([]);
    });

    it('defaults undefined condition, forceRun, and resultPaths', () => {
        const parsed = parseBuildPlanPhases(
            JSON.stringify({
                phases: [
                    {
                        name: 'test',
                        script: './gradlew test',
                        // condition, forceRun, and resultPaths intentionally omitted
                    },
                ],
            }),
        );
        expect(parsed?.phases[0].condition).toBe('ALWAYS');
        expect(parsed?.phases[0].forceRun).toBe(false);
        expect(parsed?.phases[0].resultPaths).toStrictEqual([]);
    });

    it('handles all optional fields missing simultaneously', () => {
        const parsed = parseBuildPlanPhases(
            JSON.stringify({
                phases: [
                    {
                        name: 'test',
                        script: './gradlew test',
                    },
                ],
            }),
        );
        expect(parsed).toEqual({
            phases: [
                {
                    name: 'test',
                    script: './gradlew test',
                    condition: 'ALWAYS',
                    forceRun: false,
                    resultPaths: [],
                },
            ],
        });
    });

    it('returns undefined if forceRun is not a boolean', () => {
        expect(
            parseBuildPlanPhases(
                JSON.stringify({
                    phases: [
                        {
                            name: 'test',
                            script: './gradlew test',
                            forceRun: 'not-a-boolean',
                        },
                    ],
                }),
            ),
        ).toBeUndefined();
    });

    it('returns undefined if mandatory fields are missing', () => {
        // Missing name
        expect(
            parseBuildPlanPhases(
                JSON.stringify({
                    phases: [
                        {
                            script: './gradlew test',
                        },
                    ],
                }),
            ),
        ).toBeUndefined();

        // Missing script
        expect(
            parseBuildPlanPhases(
                JSON.stringify({
                    phases: [
                        {
                            name: 'test',
                        },
                    ],
                }),
            ),
        ).toBeUndefined();
    });

    it('detects phases that expect tests before the due date', () => {
        expect(
            hasExpectedTestsBeforeDueDate({
                name: 'test',
                script: './gradlew test',
                condition: 'ALWAYS',
                forceRun: false,
                resultPaths: ['build/test-results/**/*.xml'],
            }),
        ).toBe(true);
        expect(
            hasExpectedTestsBeforeDueDate({
                name: 'after_due_date_test',
                script: './gradlew test',
                condition: 'AFTER_DUE_DATE',
                forceRun: false,
                resultPaths: ['build/test-results/**/*.xml'],
            }),
        ).toBe(false);
    });
});
