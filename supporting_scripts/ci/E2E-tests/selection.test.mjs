import { afterEach, describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { copyFileSync, mkdirSync, mkdtempSync, readFileSync, renameSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { discoverSpecs, selectTests, validateMapping } from './select-tests.mjs';
import { fileURLToPath } from 'node:url';

const root = new URL('../../../', import.meta.url);
const workflow = readFileSync(new URL('.github/workflows/ci-e2e.yml', root), 'utf8');
const temporaryRoots = [];
afterEach(() => temporaryRoots.splice(0).forEach((directory) => rmSync(directory, { recursive: true, force: true })));
function temporaryRoot() {
    const directory = mkdtempSync(join(tmpdir(), 'artemis-e2e-selection-'));
    temporaryRoots.push(directory);
    return directory;
}
function put(directory, file, content = 'fixture\n') {
    mkdirSync(dirname(join(directory, file)), { recursive: true });
    writeFileSync(join(directory, file), content);
}
function repository() {
    const directory = temporaryRoot();
    for (const file of ['determine-relevant-tests.sh', 'select-tests.mjs', 'verify-test-determination.sh', 'verify-selection.mjs', 'e2e-test-mapping.json']) {
        const target = `supporting_scripts/ci/E2E-tests/${file}`;
        mkdirSync(dirname(join(directory, target)), { recursive: true });
        copyFileSync(new URL(target, root), join(directory, target));
    }
    const mapping = JSON.parse(readFileSync(new URL('supporting_scripts/ci/E2E-tests/e2e-test-mapping.json', root), 'utf8'));
    for (const spec of Object.keys(mapping.specModules)) put(directory, `src/test/playwright/${spec}`);
    const git = (...args) => execFileSync('git', args, { cwd: directory, encoding: 'utf8', env: { ...process.env, GIT_CONFIG_NOSYSTEM: '1' } }).trim();
    git('init', '--quiet');
    git('config', 'user.email', 'fixture@example.invalid');
    git('config', 'user.name', 'Selection fixture');
    const commit = () => {
        git('add', '-A');
        git('commit', '--quiet', '-m', 'fixture');
    };
    commit();
    git('branch', 'base');
    const select = (base = 'base', fullSuite = false) => {
        const result = execFileSync('bash', ['supporting_scripts/ci/E2E-tests/determine-relevant-tests.sh', base], {
            cwd: directory,
            encoding: 'utf8',
            stdio: ['ignore', 'pipe', 'pipe'],
            env: { ...process.env, E2E_FULL_SUITE: String(fullSuite) },
        });
        return Object.fromEntries([...result.matchAll(/^OUTPUT: ([^=]+)=(.*)$/gm)].map(([, key, value]) => [key, value]));
    };
    return { directory, git, commit, select };
}

const mapping = JSON.parse(readFileSync(new URL('supporting_scripts/ci/E2E-tests/e2e-test-mapping.json', root), 'utf8'));
const paths = (result, key = 'RELEVANT_TESTS') => result[key].split(' ').filter(Boolean);
function assertPartition(result, directory) {
    const relevant = paths(result);
    const remaining = paths(result, 'REMAINING_TESTS');
    assert.equal(result.RUN_ALL_TESTS, 'false');
    assert.equal(new Set([...relevant, ...remaining]).size, relevant.length + remaining.length, 'A spec is scheduled twice');
    assert.deepEqual([...relevant, ...remaining].sort(), discoverSpecs(directory));
    assert.equal(Number(result.RELEVANT_COUNT), relevant.length);
    assert.equal(Number(result.REMAINING_COUNT), remaining.length);
    for (const smoke of mapping.alwaysRunTests) assert.ok(relevant.includes(smoke));
}

describe('E2E selector against real commit diffs', () => {
    for (const module of [
        'text',
        'quiz',
        'modeling',
        'programming',
        'fileupload',
        'assessment',
        'atlas',
        'exam',
        'communication',
        'lecture',
        'exercise',
        'tutorialgroup',
        'iris',
        'course',
    ]) {
        for (const file of [`src/main/java/de/tum/cit/aet/artemis/${module}/service/Service.java`, `src/main/webapp/app/${module}/component.ts`]) {
            it(`selects audited workflows for an ordinary production bugfix: ${file}`, () => {
                const { directory, commit, select } = repository();
                put(directory, file);
                commit();
                const result = select();
                assertPartition(result, directory);
                for (const [spec, consumers] of Object.entries(mapping.specModules)) {
                    if (consumers.includes(module)) assert.ok(paths(result).includes(spec), `Missing ${module} consumer ${spec}`);
                }
                assert.ok(paths(result).length < discoverSpecs(directory).length, 'Selection must leave unrelated workflows');
                assert.deepEqual(select('base', true), result, 'Full callers must preserve the same exact partition');
            });
        }
    }
    const crossConsumers = {
        lecture: [
            'tutorialgroup/TutorialGroupDetailLayout.spec.ts',
            'tutorialgroup/TutorialGroupEditing.spec.ts',
            'atlas/CompetencyManagement.spec.ts',
            'atlas/StudentCompetencyProgressView.spec.ts',
            'course/CourseChannelMessages.spec.ts',
            'course/CourseTabs.spec.ts',
            'course/CourseManagement.spec.ts',
            'course/FormattedMessageEditor.spec.ts',
            'iris/IrisCourseTab.spec.ts',
            'admin/UserDeletion.spec.ts',
        ],
        quiz: [
            'atlas/StudentCompetencyProgressView.spec.ts',
            'course/CourseExercise.spec.ts',
            'course/CourseTabs.spec.ts',
            'course/CourseManagement.spec.ts',
            'exercise/ExerciseImport.spec.ts',
            'exam/ExamQuizQuestionTypes.spec.ts',
        ],
        programming: [
            'exam/ExamProgrammingModes.spec.ts',
            'course/CourseTabs.spec.ts',
            'course/CourseArchive.spec.ts',
            'admin/DataExport.spec.ts',
            'admin/FeatureUsageGit.spec.ts',
            'iris/IrisCourseTab.spec.ts',
        ],
        exercise: [
            'exercise/text/TextExerciseManagement.spec.ts',
            'exercise/programming/ProgrammingExerciseParticipation.spec.ts',
            'exercise/modeling/ModelingTeamCollaboration.spec.ts',
            'exercise/file-upload/FileUploadExerciseAssessment.spec.ts',
            'exam/ExamParticipation.spec.ts',
        ],
        assessment: ['exam/ExamAssessment.spec.ts', 'exam/test-exam/TestExamAiFeedback.spec.ts', 'course/PresentationAssessments.spec.ts'],
        exam: [
            'lecture/LectureUnitContentRenders.spec.ts',
            'iris/IrisCourseTab.spec.ts',
            'tutorialgroup/TutorialGroupDetailLayout.spec.ts',
            'course/CourseDirectMessages.spec.ts',
            'exercise/modeling/ModelingExerciseParticipation.spec.ts',
            'admin/FeatureUsageGit.spec.ts',
        ],
        iris: [
            'lecture/LectureManagement.spec.ts',
            'atlas/CompetencyManagement.spec.ts',
            'exercise/programming/ProgrammingExerciseParticipation.spec.ts',
            'course/CourseChannelMessages.spec.ts',
        ],
    };
    for (const [module, consumers] of Object.entries(crossConsumers)) {
        it(`retains known cross-module ${module} workflows with Java regression tests and docs`, () => {
            const { directory, commit, select } = repository();
            put(directory, `src/main/java/de/tum/cit/aet/artemis/${module}/service/Service.java`);
            put(directory, `src/test/java/de/tum/cit/aet/artemis/${module}/ServiceTest.java`);
            put(directory, 'documentation/docs/user/feature.mdx');
            commit();
            const result = select();
            assertPartition(result, directory);
            for (const spec of consumers) assert.ok(paths(result).includes(`e2e/${spec}`), `Missing ${spec}`);
        });
    }
    for (const file of [
        'src/main/webapp/app/exam/manage/exams/update/exam-update.component.ts',
        'src/main/webapp/app/exam/manage/exams/update/exam-update.component.html',
        'src/main/webapp/app/exam/manage/services/exam-management.service.ts',
    ]) {
        it(`uses the audited exact client surface for ${file}`, () => {
            const { directory, commit, select } = repository();
            put(directory, file);
            commit();
            const result = select();
            assertPartition(result, directory);
            assert.ok(paths(result).includes('e2e/exam/ExamManagement.spec.ts'));
            assert.ok(paths(result).includes('e2e/exercise/quiz-exercise/QuizExerciseManagement.spec.ts'));
            assert.ok(paths(result).includes('e2e/course/CourseManagement.spec.ts'));
            assert.ok(!paths(result).includes('e2e/lecture/LectureUnitContentRenders.spec.ts'));
            assert.ok(!paths(result).includes('e2e/tutorialgroup/TutorialGroupDetailLayout.spec.ts'));
        });
    }
    for (const broad of [
        'src/main/webapp/app/exam/overview/services/exam-participation.service.ts',
        'src/main/webapp/app/exam/manage/services/new-exam-service.ts',
        'src/main/java/de/tum/cit/aet/artemis/exam/service/ExamService.java',
    ]) {
        it(`unions a scoped client change with the broader exam consumer set for ${broad}`, () => {
            const { directory, commit, select } = repository();
            put(directory, 'src/main/webapp/app/exam/manage/exams/update/exam-update.component.ts');
            put(directory, broad);
            commit();
            const result = select();
            assertPartition(result, directory);
            assert.ok(paths(result).includes('e2e/lecture/LectureUnitContentRenders.spec.ts'));
            assert.ok(paths(result).includes('e2e/tutorialgroup/TutorialGroupDetailLayout.spec.ts'));
            assert.ok(paths(result).includes('e2e/exercise/quiz-exercise/QuizExerciseManagement.spec.ts'));
        });
    }
    it('keeps management-only tutorial editing out of the course-container exam dependency', () => {
        const { directory, commit, select } = repository();
        put(directory, 'src/main/webapp/app/exam/overview/services/exam-participation.service.ts');
        commit();
        const result = select();
        assertPartition(result, directory);
        assert.ok(!paths(result).includes('e2e/tutorialgroup/TutorialGroupEditing.spec.ts'));
    });
    it('selects a client component, its local spec, docs and a module translation catalog', () => {
        const { directory, commit, select } = repository();
        put(directory, 'src/main/webapp/app/quiz/manage/quiz.component.ts');
        put(directory, 'src/main/webapp/app/quiz/manage/quiz.component.spec.ts');
        put(directory, 'src/main/webapp/i18n/de/exercise.json');
        put(directory, 'documentation/docs/user/quiz.mdx');
        commit();
        const result = select();
        assertPartition(result, directory);
        assert.ok(paths(result).includes('e2e/exercise/ExerciseImport.spec.ts'));
    });
    for (const file of [
        'src/main/java/de/tum/cit/aet/artemis/newmodule/Endpoint.java',
        'src/main/webapp/app/shared/table.ts',
        'src/main/webapp/app/core/bootstrap.ts',
        'src/main/java/de/tum/cit/aet/artemis/core/service/distributed/Provider.java',
        'src/main/java/de/tum/cit/aet/artemis/account/AccountService.java',
        'src/main/java/de/tum/cit/aet/artemis/quiz/security/QuizSecurity.java',
        'src/main/java/de/tum/cit/aet/artemis/quiz/config/QuizConfiguration.java',
        'src/main/java/de/tum/cit/aet/artemis/localci/service/BuildQueue.java',
        'src/main/resources/config/application.yml',
        'src/test/playwright/support/fixture.ts',
        'src/test/playwright/fixtures/quiz.json',
        'src/test/playwright/e2e/shared/helpers.ts',
        'src/main/webapp/i18n/en/global.json',
        'src/main/webapp/i18n/en/new-module.json',
        'src/test/resources/config/application.yml',
        'src/test/playwright/e2e/space name.spec.ts',
        'unknown/path\nwith-newline',
        'src/main/webapp/app/quiz/question.ts\n',
    ]) {
        it(`fails closed, even mixed with mapped source, for ${file}`, () => {
            const { directory, commit, select } = repository();
            put(directory, 'src/main/webapp/app/quiz/question.ts');
            put(directory, file);
            commit();
            assert.equal(select().RUN_ALL_TESTS, 'true');
            assert.equal(select('base', true).RUN_ALL_TESTS, 'true');
        });
    }
    it('fails closed for a new spec until its runtime consumers are audited', () => {
        const { directory, commit, select } = repository();
        put(directory, 'src/test/playwright/e2e/quiz/NewConsumer.spec.ts');
        commit();
        assert.equal(select().RUN_ALL_TESTS, 'true');
    });
    it('fails closed when an assigned spec adds an unaudited runtime consumer', () => {
        const { directory, commit, select } = repository();
        put(directory, 'src/main/webapp/app/quiz/question.ts');
        put(directory, 'src/test/playwright/e2e/tutorialgroup/TutorialGroupEditing.spec.ts', 'await exerciseAPIRequests.createQuizExercise({});\n');
        commit();
        assert.equal(select().RUN_ALL_TESTS, 'true');
    });
    it('fails closed when a smoke spec was removed', () => {
        const { directory, commit, select } = repository();
        rmSync(join(directory, 'src/test/playwright/e2e/Login.spec.ts'));
        commit();
        assert.equal(select().RUN_ALL_TESTS, 'true');
    });
    for (const malformed of ['{invalid', JSON.stringify({ ...mapping, specModules: {} }), JSON.stringify({ ...mapping, alwaysRunTests: [] })]) {
        it('fails closed when the mapping contract is invalid', () => {
            const { directory, commit, select } = repository();
            put(directory, 'supporting_scripts/ci/E2E-tests/e2e-test-mapping.json', malformed);
            commit();
            assert.equal(select().RUN_ALL_TESTS, 'true');
        });
    }
    it('selects the full suite for an empty committed diff', () => assert.equal(repository().select().RUN_ALL_TESTS, 'true'));
    it('does not substitute the last commit when the PR base is unavailable', () => {
        const { directory, commit, select } = repository();
        put(directory, 'src/main/webapp/app/quiz/question.ts');
        commit();
        assert.equal(select('missing-base').RUN_ALL_TESTS, 'true');
    });
    it('uses the merge base without including unrelated changes on the base branch', () => {
        const { directory, git, commit, select } = repository();
        git('checkout', '--quiet', 'base');
        put(directory, 'unknown-global-file');
        commit();
        git('checkout', '--quiet', '-');
        put(directory, 'src/main/webapp/app/quiz/question.ts');
        commit();
        assertPartition(select(), directory);
    });
    it('selects both consumers when a source file is renamed across modules', () => {
        const { directory, git, commit, select } = repository();
        const before = 'src/main/webapp/app/quiz/question.ts';
        const after = 'src/main/webapp/app/lecture/question.ts';
        put(directory, before);
        commit();
        git('branch', '-f', 'base', 'HEAD');
        mkdirSync(dirname(join(directory, after)), { recursive: true });
        renameSync(join(directory, before), join(directory, after));
        commit();
        const result = select();
        assertPartition(result, directory);
        assert.ok(paths(result).includes('e2e/exercise/quiz-exercise/QuizExerciseParticipation.spec.ts'));
        assert.ok(paths(result).includes('e2e/lecture/LectureManagement.spec.ts'));
    });
    it('fails closed when an exact client scope refers to a removed consumer spec', () => {
        const { directory, commit, select } = repository();
        rmSync(join(directory, 'src/test/playwright/e2e/exam/ExamManagement.spec.ts'));
        put(directory, 'src/main/webapp/app/exam/manage/exams/update/exam-update.component.ts');
        commit();
        assert.equal(select().RUN_ALL_TESTS, 'true');
    });
    it('keeps deleted specs out of runner arguments and retains the full complement', () => {
        const { directory, commit, select } = repository();
        rmSync(join(directory, 'src/test/playwright/e2e/exercise/modeling/ModelingExerciseParticipation.spec.ts'));
        commit();
        const result = select();
        assertPartition(result, directory);
        assert.deepEqual(paths(result), [...mapping.alwaysRunTests].sort());
    });
    it('partitions nested test-exam specs exactly once for a spec-only full override', () => {
        const { directory, commit, select } = repository();
        const spec = 'e2e/exam/test-exam/TestExamParticipation.spec.ts';
        put(directory, `src/test/playwright/${spec}`, 'changed\n');
        commit();
        const result = select('base', true);
        assertPartition(result, directory);
        assert.deepEqual(paths(result), [...mapping.alwaysRunTests, spec].sort());
        assert.ok(paths(result, 'REMAINING_TESTS').includes('e2e/exam/test-exam/TestExamManagement.spec.ts'));
    });
});

describe('verification CLI uses the production selector', () => {
    const directory = fileURLToPath(root);
    function verify(...modules) {
        return execFileSync('bash', ['supporting_scripts/ci/E2E-tests/verify-test-determination.sh', ...modules], {
            cwd: directory,
            encoding: 'utf8',
            stdio: ['ignore', 'pipe', 'pipe'],
        });
    }
    for (const module of ['tutorialgroup', 'exam-management-client', 'core']) {
        it(`verifies the actual ${module} scope and prints its exact counts`, () => {
            const entry = mapping.mappings[module];
            const files = entry.sourcePaths.map((path) => (path.endsWith('/') ? `${path}SelectionVerification.${path.startsWith('src/main/java/') ? 'java' : 'ts'}` : path));
            const result = selectTests(directory, files, mapping);
            const total = discoverSpecs(directory).length;
            const output = verify(module);
            assert.match(output, new RegExp(`Selected specs: ${result.runAll ? total : result.relevant.length}\\b`));
            assert.match(output, new RegExp(`Remaining specs: ${result.runAll ? 0 : result.remaining.length}\\b`));
            assert.ok(output.includes(`Reason: ${result.reason}`));
            assert.match(output, result.runAll ? /Full suite/ : /Disjoint exact partition/);
            assert.doesNotMatch(output, /acceptable overlap/);
        });
    }
    it('verifies every mapping separately with --all, including globals and the exact client scope', () => {
        const output = verify('--all');
        assert.equal((output.match(/^RESULT: PASS/gm) ?? []).length, Object.keys(mapping.mappings).length);
        assert.match(output, /Modules: exam-management-client/);
        assert.match(output, /Modules: core/);
    });
    it('verifies a combined module input through the same production selection', () => {
        const modules = ['tutorialgroup', 'exam-management-client'];
        const files = modules.flatMap((module) =>
            mapping.mappings[module].sourcePaths.map((path) => (path.endsWith('/') ? `${path}SelectionVerification.${path.startsWith('src/main/java/') ? 'java' : 'ts'}` : path)),
        );
        const result = selectTests(directory, files, mapping);
        const output = verify(...modules);
        assert.match(output, new RegExp(`Selected specs: ${result.relevant.length}\\b`));
        assert.match(output, new RegExp(`Remaining specs: ${result.remaining.length}\\b`));
        assert.ok(output.includes(`Reason: ${result.reason}`));
    });
    it('rejects an invalid mapping rather than treating its full fallback as verification success', () => {
        const fixture = repository();
        put(fixture.directory, 'supporting_scripts/ci/E2E-tests/e2e-test-mapping.json', JSON.stringify({ ...mapping, specModules: {} }));
        assert.throws(
            () =>
                execFileSync('bash', ['supporting_scripts/ci/E2E-tests/verify-test-determination.sh', 'core'], {
                    cwd: fixture.directory,
                    encoding: 'utf8',
                    stdio: ['ignore', 'pipe', 'pipe'],
                }),
            (error) => error.status !== 0 && /RESULT: FAIL - Invalid or incomplete exact client scope/.test(error.stderr),
        );
    });
    it('rejects an unknown module instead of approving smoke plus a complement', () => {
        assert.throws(
            () => verify('unknown-module'),
            (error) => error.status !== 0 && /Unknown module/.test(error.stderr),
        );
    });
});

describe('repository runtime consumer contract', () => {
    const directory = fileURLToPath(root);
    const specs = discoverSpecs(directory);
    it('fails closed for an invalid exact client scope contract', () => {
        const file = 'src/main/webapp/app/exam/manage/exams/update/exam-update.component.ts';
        for (const override of [{ replacesModule: 'missing' }, { sourcePaths: ['src/main/webapp/app/exam/manage/'] }, { audit: '' }]) {
            const invalid = JSON.parse(JSON.stringify(mapping));
            Object.assign(invalid.mappings['exam-management-client'], override);
            assert.equal(selectTests(directory, [file], invalid).runAll, true);
        }
    });
    it('accounts for every current spec, including previously undiscovered groups', () => {
        validateMapping(mapping, specs);
        assert.deepEqual(Object.keys(mapping.specModules).sort(), specs);
    });
    it('selects every documented consumer and an exact full-mode complement for each mapped module', () => {
        for (const [module, entry] of Object.entries(mapping.mappings)) {
            const file = entry.sourcePaths[0].endsWith('/') ? `${entry.sourcePaths[0]}service/Selection.java` : entry.sourcePaths[0];
            const result = selectTests(directory, [file], mapping);
            if (mapping.runAllTestsPatterns.some((prefix) => file.startsWith(prefix)) || !specs.some((spec) => mapping.specModules[spec].includes(module))) continue;
            assert.equal(result.runAll, false, `${module}: ${result.reason}`);
            assert.deepEqual([...result.relevant, ...result.remaining].sort(), specs);
            for (const spec of specs.filter((spec) => mapping.specModules[spec].includes(module))) assert.ok(result.relevant.includes(spec));
        }
    });
    it('keeps direct HTTP and exercise dispatch consumers in the reviewed contract', () => {
        for (const spec of specs) {
            const source = readFileSync(join(directory, 'src/test/playwright', spec), 'utf8')
                .replace(/^import\b[\s\S]*?;/gm, '')
                .replace(/\/\*[\s\S]*?\*\/|^\s*\/\/[^\n]*/gm, '');
            const observed = new Set([...source.matchAll(/\bapi\/(\w+)\//g)].map((match) => match[1]).filter((module) => Object.hasOwn(mapping.mappings, module)));
            for (const [module, type] of Object.entries({ quiz: 'QUIZ', programming: 'PROGRAMMING', text: 'TEXT', modeling: 'MODELING', fileupload: 'FILE_UPLOAD' })) {
                const title = module === 'fileupload' ? 'FileUpload' : module[0].toUpperCase() + module.slice(1);
                if (new RegExp(`ExerciseType\\.${type}\\b|\\b\\w*${title}Exercise\\w*\\(`).test(source)) observed.add(module);
            }
            for (const [module, pattern] of Object.entries({
                lecture: /\b(?:createLecture|deleteLecture|createTextUnit)\(|\blecture(?:Management|Creation)\./,
                atlas: /\b(?:createCompetency|deleteCompetency|createCompetencyRelation|createPrerequisite|enableLearningPaths)\(|\bcompetencyManagement\./,
                communication: /\b(?:communicationAPIRequests|courseMessages|courseCommunication)\./,
                exam: /\b(?:adminExamAPIRequests|examAPIRequests|examExerciseGroupCreation|examParticipation|examTestRun|examCreation|studentExamManagement)\./,
            }))
                if (pattern.test(source)) observed.add(module);
            for (const module of observed) assert.ok(mapping.specModules[spec].includes(module), `${spec} consumes ${module} at runtime; update the contract`);
        }
    });
});

// Execute the workflow's actual block scalars; no duplicate copy of the gate or PR comment logic.
function scalar(source, key, indent) {
    const lines = source.split('\n');
    const start = lines.findIndex((line) => line === `${' '.repeat(indent)}${key}: |`);
    assert.notEqual(start, -1, `Missing ${key} block`);
    const result = [];
    for (const line of lines.slice(start + 1)) {
        if (line.trim() && !line.startsWith(' '.repeat(indent + 2))) break;
        result.push(line.slice(indent + 2));
    }
    return result.join('\n');
}
function job(name) {
    const start = workflow.indexOf(`\n  ${name}:\n`);
    assert.notEqual(start, -1);
    return workflow.slice(start).split(/\n  [\w-]+:\n/)[1];
}
function gate(overrides = {}) {
    const output = join(temporaryRoot(), 'output');
    const summary = `${output}-summary`;
    const env = {
        PATH: process.env.PATH,
        GITHUB_OUTPUT: output,
        GITHUB_STEP_SUMMARY: summary,
        IS_PR: 'true',
        RUN_FULL_SUITE: 'false',
        RUN_ALL_TESTS: 'false',
        RELEVANT_TESTS: 'e2e/Login.spec.ts',
        REMAINING_TESTS: 'e2e/exercise/text/',
        DETERMINE_JOB: 'success',
    };
    for (const phase of ['RELEVANT', 'REMAINING', 'ALL_PR', 'ALL_NON_PR']) {
        Object.assign(env, { [`${phase}_RESULT`]: '', [`${phase}_CLASS`]: '', [`${phase}_INFRA`]: '', [`${phase}_JOB`]: 'skipped' });
    }
    Object.assign(env, { RELEVANT_JOB: 'success', RELEVANT_RESULT: 'success', RELEVANT_CLASS: 'clean', RELEVANT_INFRA: 'false' }, overrides);
    execFileSync('bash', ['-c', scalar(job('report-results'), 'run', 8)], { env });
    return Object.fromEntries(
        readFileSync(output, 'utf8')
            .trim()
            .split('\n')
            .map((line) => line.split(/=(.*)/s).slice(0, 2)),
    );
}
function phase2Selected(runFullSuite) {
    const expression = scalar(job('run-e2e-remaining'), 'if', 4).replaceAll('needs.determine-tests', 'needs.determine').replaceAll('needs.run-e2e-relevant', 'needs.relevant');
    return Function(
        'inputs',
        'needs',
        'cancelled',
        'contains',
        'fromJSON',
        `return (${expression});`,
    )(
        { is_pr: true, run_full_suite: runFullSuite },
        {
            determine: { result: 'success', outputs: { run_all_tests: 'false', remaining_tests: 'e2e/exercise/text/' } },
            relevant: { result: 'success', outputs: { infrastructure_failed: 'false', phase_result: 'clean' } },
        },
        () => false,
        (list, value) => list.includes(value),
        JSON.parse,
    );
}

describe('workflow selection and required report contract', () => {
    it('passes full-mode intent without weakening module selection', () => {
        assert.match(job('determine-tests'), /determine-relevant-tests\.sh base_branch/);
        assert.doesNotMatch(readFileSync(new URL('supporting_scripts/ci/E2E-tests/select-tests.mjs', root), 'utf8'), /process\.env\.E2E_FULL_SUITE/);
    });
    it('does not schedule Phase 2 in selective PR mode', () => assert.equal(phase2Selected(false), false));
    it('retains Phase 2 for full-suite callers', () => assert.equal(phase2Selected(true), true));
    it('passes an intentional Phase 2 omission with explicit selected coverage', () => {
        const result = gate();
        assert.equal(result.conclusion, 'success');
        assert.match(result.scope, /Selected.*smoke/);
        assert.match(result.description, /Selected E2E tests passed/);
    });
    for (const overrides of [
        { RELEVANT_JOB: 'skipped', RELEVANT_RESULT: '', RELEVANT_CLASS: '', RELEVANT_INFRA: '' },
        { RELEVANT_RESULT: '' },
        { RUN_ALL_TESTS: 'true' },
        { RUN_FULL_SUITE: 'true' },
        { IS_PR: 'false', DETERMINE_JOB: 'skipped' },
    ]) {
        it(`blocks a missing expected phase: ${JSON.stringify(overrides)}`, () => assert.equal(gate(overrides).conclusion, 'failure'));
    }
    it('reports incomplete full-suite execution as an error instead of a green raw status', () => {
        const result = gate({ RUN_FULL_SUITE: 'true' });
        assert.equal(result.status, 'error');
    });
    it('passes a completed full two-phase suite', () => {
        const result = gate({ RUN_FULL_SUITE: 'true', REMAINING_JOB: 'success', REMAINING_RESULT: 'success', REMAINING_CLASS: 'clean', REMAINING_INFRA: 'false' });
        assert.equal(result.conclusion, 'success');
        assert.equal(result.scope, 'Full suite');
    });
    it('passes a completed full-suite fallback even when selective coverage was requested', () => {
        const result = gate({
            RUN_ALL_TESTS: 'true',
            RELEVANT_JOB: 'skipped',
            RELEVANT_RESULT: '',
            RELEVANT_CLASS: '',
            RELEVANT_INFRA: '',
            ALL_PR_JOB: 'success',
            ALL_PR_RESULT: 'success',
            ALL_PR_CLASS: 'clean',
            ALL_PR_INFRA: 'false',
        });
        assert.equal(result.conclusion, 'success');
        assert.equal(result.scope, 'Full suite');
        assert.equal(result.phase2_intentionally_skipped, 'false');
    });
    it('passes the completed non-PR full-suite path used by develop, merge groups, and manual builds', () => {
        const result = gate({
            IS_PR: 'false',
            DETERMINE_JOB: 'skipped',
            RELEVANT_JOB: 'skipped',
            RELEVANT_RESULT: '',
            RELEVANT_CLASS: '',
            RELEVANT_INFRA: '',
            ALL_NON_PR_JOB: 'success',
            ALL_NON_PR_RESULT: 'success',
            ALL_NON_PR_CLASS: 'clean',
            ALL_NON_PR_INFRA: 'false',
        });
        assert.equal(result.conclusion, 'success');
        assert.equal(result.scope, 'Full suite');
    });
    it('preserves the blocking Phase 1 verdict when full-mode Phase 2 fails fast', () => {
        const result = gate({ RUN_FULL_SUITE: 'true', RELEVANT_RESULT: 'failure', RELEVANT_CLASS: 'real' });
        assert.equal(result.conclusion, 'failure');
        assert.match(result.description, /non-flaky/);
    });
    it('blocks cancellation of a selected phase', () => assert.equal(gate({ RELEVANT_JOB: 'cancelled', RELEVANT_RESULT: '' }).conclusion, 'failure'));
    it('exonerates only completed selected tests with known flaky history', () =>
        assert.equal(gate({ RELEVANT_RESULT: 'failure', RELEVANT_CLASS: 'flaky' }).conclusion, 'neutral'));
    it('keeps unknown failures blocking in selective mode', () => assert.equal(gate({ RELEVANT_RESULT: 'failure', RELEVANT_CLASS: 'unknown' }).conclusion, 'failure'));
});

describe('PR comment scope reporting', () => {
    const AsyncFunction = Object.getPrototypeOf(async () => {}).constructor;
    const context = { repo: { owner: 'fixture', repo: 'Artemis' }, serverUrl: 'https://github.invalid', runId: 1 };
    async function finalComment(existing = '') {
        const script = scalar(job('report-results').split('      - name: Update E2E comment with overall status')[1], 'script', 10);
        let body;
        const issues = {
            getComment: async () => ({ data: { body: existing } }),
            updateComment: async (data) => {
                body = data.body;
            },
            createComment: async (data) => {
                body = data.body;
            },
        };
        await new AsyncFunction('github', 'context', 'process', script)({ rest: { issues } }, context, {
            env: {
                INPUT_PR_NUMBER: '1',
                INPUT_COMMENT_ID: existing ? '2' : '',
                INPUT_CONCLUSION: 'success',
                INPUT_DESCRIPTION: 'Selected E2E tests passed',
                INPUT_SCOPE: 'Selected relevant + smoke specs',
                INPUT_PHASE2_INTENTIONALLY_SKIPPED: 'true',
                INPUT_PHASE1_RESULT: 'success',
                INPUT_PHASE2_RESULT: 'skipped',
            },
        });
        return body;
    }
    it('makes an intentionally omitted Phase 2 explicit when finalizing an existing comment', async () => {
        const body = await finalComment('<!-- PHASE2 -->pending<!-- /PHASE2 -->\n<!-- OVERALL -->');
        assert.match(body, /Intentionally skipped \(selective PR coverage\)/);
        assert.match(body, /Selected relevant \+ smoke specs/);
        assert.match(body, /full-suite coverage was not requested/);
        assert.doesNotMatch(body, /no remaining tests/);
    });
    it('reports selective scope even when Phase 1 could not create its comment', async () => {
        const body = await finalComment();
        assert.match(body, /Selected E2E tests passed/);
        assert.match(body, /Phase 2 intentionally omitted/);
        assert.match(body, /full-suite coverage was not requested/);
    });
    it('shows selective scope and the omitted paths in the Phase 1 comment', async () => {
        const script = scalar(job('run-e2e-relevant').split('      - name: Post E2E comment (Phase 1)')[1] || '', 'script', 10);
        let body;
        const require = () => ({ parseFailedTests: () => [], fetchFlakinessScores: async () => [], buildFlakinessTable: () => '' });
        const github = {
            rest: {
                issues: {
                    createComment: async (data) => {
                        body = data.body;
                    },
                },
            },
        };
        await new AsyncFunction('require', 'github', 'context', 'process', script)(require, github, context, {
            env: {
                INPUT_PR_NUMBER: '1',
                INPUT_RELEVANT_TESTS: 'e2e/Login.spec.ts',
                INPUT_REMAINING_TESTS: 'e2e/exam/',
                INPUT_RUN_FULL_SUITE: 'false',
            },
        });
        assert.match(body, /Intentionally skipped/);
        assert.match(body, /Selected relevant \+ smoke specs/);
        assert.match(body, /Phase 2 \(not selected\).*e2e\/exam\//);
        assert.doesNotMatch(body, /Pending/);
    });
});
