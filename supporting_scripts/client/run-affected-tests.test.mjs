import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { afterEach, test } from 'node:test';
import { executePlan, planTests, readChanges, runRelatedProject } from './run-affected-tests.mjs';

const directories = [];
const app = 'src/main/webapp/app';
const component = `${app}/lecture/widget/widget.component.ts`;
const template = `${app}/lecture/widget/widget.component.html`;
const stylesheet = `${app}/lecture/widget/widget.component.scss`;
const change = (file, status = 'M') => ({ status, paths: [file] });

function fixture(files = {}) {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'artemis-affected-client-'));
    directories.push(root);
    for (const [file, contents] of Object.entries(files)) {
        fs.mkdirSync(path.dirname(path.join(root, file)), { recursive: true });
        fs.writeFileSync(path.join(root, file), contents);
    }
    return { root, files: Object.keys(files) };
}

afterEach(() => {
    for (const directory of directories.splice(0)) fs.rmSync(directory, { recursive: true, force: true });
});

test('selects changed application TypeScript as graph seeds rather than just nearby specs', async () => {
    const context = fixture({ [component]: 'export const answer = 42;' });
    const plan = await planTests({ ...context, changes: [change(component)] });
    assert.equal(plan.mode, 'affected');
    assert.deepEqual(plan.sources, [component]);
});

test('adds every template owner so Vitest can find indirect consumers and shared templates', async () => {
    const another = `${app}/lecture/another.component.ts`;
    const context = fixture({
        [component]: "@Component({ templateUrl: './widget.component.html' }) class Widget {}",
        [another]: "@Component({ templateUrl: './widget/widget.component.html' }) class Another {}",
        [template]: '<span>changed</span>',
    });
    const plan = await planTests({ ...context, changes: [change(template)] });
    assert.equal(plan.mode, 'affected');
    assert.deepEqual(plan.sources, [another, component].sort());
});

test('follows Sass partials, forwards, root load paths and cycles to every component owner', async () => {
    const partial = `${app}/lecture/styles/_colors.scss`;
    const barrel = `${app}/lecture/styles/_index.scss`;
    const context = fixture({
        [component]: "@Component({ styleUrls: ['./widget.component.scss'] }) class Widget {}",
        [stylesheet]: "@use '../styles';",
        [partial]: "@use 'index'; $color: red;",
        [barrel]: "@forward 'src/main/webapp/app/lecture/styles/colors';",
    });
    const plan = await planTests({ ...context, changes: [change(partial)] });
    assert.equal(plan.mode, 'affected');
    assert.deepEqual(plan.sources, [component]);
});

test('includes CSS imports and resources in test components', async () => {
    const spec = `${app}/lecture/widget/widget.component.spec.ts`;
    const css = `${app}/lecture/widget/widget.css`;
    const context = fixture({
        [spec]: "@Component({ styleUrl: './widget.css', templateUrl: './widget.component.html' }) class Host {}",
        [component]: "import './widget.css?inline';",
        [css]: '.widget { color: red; }',
        [template]: '<span>host</span>',
    });
    const plan = await planTests({ ...context, changes: [change(css), change(template)] });
    assert.equal(plan.mode, 'affected');
    assert.deepEqual(plan.sources, [spec, component].sort());
});

test('recognizes aliased Angular Component imports when expanding template owners', async () => {
    const context = fixture({
        [component]: "import { Component as NgComponent } from '@angular/core'; @NgComponent({ templateUrl: './widget.component.html' }) class Widget {}",
        [template]: '<span>changed</span>',
    });
    const plan = await planTests({ ...context, changes: [change(template)] });
    assert.equal(plan.mode, 'affected');
    assert.deepEqual(plan.sources, [component]);
});

test('includes resource consumers using the same root aliases as the Vitest configuration', async () => {
    const aliasConsumer = `${app}/lecture/alias-consumer.ts`;
    const context = fixture({
        [component]: "@Component({ styleUrl: './widget.component.scss' }) class Widget {}",
        [aliasConsumer]: "import '/src/main/webapp/app/lecture/widget/widget.component.scss?inline';",
        [stylesheet]: ':host { display: block; }',
    });
    const plan = await planTests({ ...context, changes: [change(stylesheet)] });
    assert.equal(plan.mode, 'affected');
    assert.deepEqual(plan.sources, [aliasConsumer, component].sort());
});

for (const files of [
    { [template]: '<p>orphan</p>' },
    { [template]: '<p>dynamic</p>', [component]: '@Component({ templateUrl: getTemplate() }) class Widget {}' },
    { [stylesheet]: '@use "#{theme}";', [component]: "@Component({ styleUrl: './widget.component.scss' }) class Widget {}" },
    { [stylesheet]: '@import $theme;', [component]: "@Component({ styleUrl: './widget.component.scss' }) class Widget {}" },
    { [stylesheet]: '@import url(other.css);', [component]: "@Component({ styleUrl: './widget.component.scss' }) class Widget {}" },
    { [stylesheet]: "@import './other.scss'", [component]: "@Component({ styleUrl: './widget.component.scss' }) class Widget {}" },
    { [stylesheet]: '@include meta.load-css($theme);', [component]: "@Component({ styleUrl: './widget.component.scss' }) class Widget {}" },
    { [template]: '<p>spread</p>', [component]: "@Component({ ...metadata, templateUrl: './widget.component.html' }) class Widget {}" },
]) {
    test(`falls back to the full suite when resource ownership is uncertain: ${Object.values(files)[0]}`, async () => {
        const context = fixture(files);
        const plan = await planTests({ ...context, changes: [change(Object.keys(files)[0])] });
        assert.equal(plan.mode, 'full');
        assert.match(plan.reason, /resource|Sass|metadata/i);
    });
}

for (const file of [
    'package.json',
    'pnpm-lock.yaml',
    'pnpm-workspace.yaml',
    'tsconfig.json',
    'angular.json',
    '.npmrc',
    'config/testing/vitest.monaco.config.ts',
    'config/client/tsconfig.spec.json',
    'config/pnpm/patches/dependency.patch',
    'vitest.config.ts',
    'vite.config.ts',
    'packages/tum-aet-ui/src/button.ts',
    'src/test/javascript/spec/vitest-test-setup.ts',
    'src/test/javascript/spec/helpers/mocks/mock-monaco-editor.ts',
    'src/test/vitest/test-utils.ts',
    `${app}/shared/util.ts`,
    `${app}/foundation/util/deep-clone.util.ts`,
    `${app}/shared-ui/button.ts`,
    `${app}/core/config/dayjs.ts`,
    `${app}/core/environments/environment.ts`,
    `${app}/openapi/model.ts`,
    `${app}/app.config.ts`,
    'src/main/webapp/content/scss/_variables.scss',
    'src/main/webapp/i18n/en/global.json',
    `${app}/lecture/widget/unknown.wasm`,
    'supporting_scripts/client/prebuild.mjs',
]) {
    test(`uses full validation for shared infrastructure or unsupported client input: ${file}`, async () => {
        const plan = await planTests({ ...fixture(), changes: [change(file)] });
        assert.equal(plan.mode, 'full');
    });
}

for (const file of [
    '.nvmrc',
    '.node-version',
    '.java-version',
    '.tool-versions',
    '.env',
    'build.gradle',
    'build.gradle.kts',
    'gradle.properties',
    'gradle/profile_dev.gradle',
    'gradle/profile_prod.gradle',
    'gradle/wrapper/gradle-wrapper.properties',
    'settings.gradle',
    'settings.gradle.kts',
    'gradlew',
    'gradlew.bat',
    'buildSrc/src/main/kotlin/ClientConvention.kt',
    'new-global-input.toml',
    'new-tooling/client-transform.js',
    'src/main/new-generated-input.json',
    'src/test/playwright/new-test.spec.ts',
    'src/main/java-client/future.ts',
    'documentation-tools/transform.js',
]) {
    test(`global or unclassified inputs require the full client suite: ${file}`, async () => {
        const plan = await planTests({ ...fixture(), changes: [change(file)] });
        assert.equal(plan.mode, 'full');
    });
}

for (const file of [
    'README.md',
    'LICENSE',
    '.github/README.md',
    'skills/new-procedure/SKILL.md',
    'supporting_scripts/client/README.md',
    'documentation/docs/example.mdx',
    'src/main/java/Service.java',
    'src/test/java/ServiceTest.java',
    'src/main/resources/config/application.yml',
    'src/test/resources/example.json',
]) {
    test(`explicit server or documentation inputs can skip client tests: ${file}`, async () => {
        for (const status of ['M', 'D']) {
            const plan = await planTests({ ...fixture(), changes: [change(file, status)] });
            assert.equal(plan.mode, 'none');
        }
    });
}

test('deletions and renames use full validation, including renames across the client boundary', async () => {
    for (const changes of [
        [change(component, 'D')],
        [{ status: 'R100', paths: [component, 'documentation/old-example.ts'] }],
        [{ status: 'R100', paths: ['documentation/old-example.mdx', 'new-global-input.toml'] }],
    ]) {
        const plan = await planTests({ ...fixture(), changes });
        assert.equal(plan.mode, 'full');
    }
});

test('server and documentation changes need no client test process', async () => {
    const plan = await planTests({
        ...fixture(),
        changes: [change('src/main/java/Service.java'), change('documentation/docs/example.mdx', 'D')],
    });
    assert.equal(plan.mode, 'none');
});

test('safe server and documentation changes preserve affected client selection in a mixed pull request', async () => {
    const plan = await planTests({
        ...fixture({ [component]: 'export const answer = 42;' }),
        changes: [change('src/main/java/Service.java'), change('documentation/docs/example.mdx'), change('README.md'), change(component)],
    });
    assert.equal(plan.mode, 'affected');
    assert.deepEqual(plan.sources, [component]);
});

test('both ordinary and real Monaco integration specs can be selected directly', async () => {
    const normal = 'src/test/javascript/spec/integration/code-editor/editor.spec.ts';
    const monaco = 'src/test/javascript/spec/integration/monaco-editor/editor.spec.ts';
    const plan = await planTests({ ...fixture(), changes: [change(normal, 'A'), change(monaco)] });
    assert.equal(plan.mode, 'affected');
    assert.deepEqual(plan.sources, [normal, monaco]);
});

function git(root, args) {
    return execFileSync('git', args, { cwd: root, encoding: 'utf8', stdio: 'pipe' }).trim();
}

test('uses the merge base of the fetched ref and handles spaces and rename records without splitting paths', () => {
    const file = `${app}/lecture/with space.ts`;
    const renamed = `${app}/lecture/renamed file.ts`;
    const context = fixture({ [file]: 'export const answer = 42;\n' });
    git(context.root, ['init', '--initial-branch=develop']);
    git(context.root, ['config', 'user.name', 'Affected Tests']);
    git(context.root, ['config', 'user.email', 'test@example.com']);
    git(context.root, ['add', '.']);
    git(context.root, ['commit', '-m', 'Base']);
    git(context.root, ['checkout', '-b', 'pr']);
    git(context.root, ['mv', file, renamed]);
    git(context.root, ['commit', '-m', 'Move component']);
    git(context.root, ['checkout', 'develop']);
    fs.writeFileSync(path.join(context.root, 'README.md'), 'Unrelated base advance');
    git(context.root, ['add', '.']);
    git(context.root, ['commit', '-m', 'Advance base']);
    git(context.root, ['checkout', 'pr']);

    assert.deepEqual(readChanges(context.root, 'develop'), [{ status: 'R100', paths: [file, renamed] }]);
    assert.throws(() => readChanges(context.root, 'not-fetched'), /not-fetched|revision|commit/i);
});

test('does not silently omit uncommitted or untracked client changes in local use', () => {
    const context = fixture({ [component]: 'export const answer = 42;\n' });
    git(context.root, ['init', '--initial-branch=develop']);
    git(context.root, ['config', 'user.name', 'Affected Tests']);
    git(context.root, ['config', 'user.email', 'test@example.com']);
    git(context.root, ['add', '.']);
    git(context.root, ['commit', '-m', 'Base']);
    fs.writeFileSync(path.join(context.root, component), 'export const answer = 43;\n');
    fs.writeFileSync(path.join(context.root, template), '<p>new</p>');
    assert.deepEqual(readChanges(context.root, 'develop'), [change(component), change(template, 'A')]);
});

function executionContext(overrides = {}) {
    const { root } = fixture();
    const commands = [];
    const projects = [];
    const messages = [];
    const outputFile = path.join(root, 'github-output');
    return {
        root,
        commands,
        projects,
        messages,
        outputFile,
        runCommand: async (args) => commands.push(args),
        runRelatedProject: async (options) => {
            projects.push(options);
            return { selected: 1, success: true };
        },
        log: (message) => messages.push(message),
        ...overrides,
    };
}

test('full mode preserves the established suite and coverage contract without running selection', async () => {
    const context = executionContext();
    await executePlan({ mode: 'full', reason: 'forced' }, context);
    assert.deepEqual(context.commands, [['run', 'test:ci']]);
    assert.deepEqual(context.projects, []);
    assert.equal(fs.readFileSync(context.outputFile, 'utf8'), 'mode=full\ncoverage=true\n');
});

test('affected mode prebuilds once, selects both projects, and produces no coverage', async () => {
    const context = executionContext();
    const sources = [component];
    const result = await executePlan({ mode: 'affected', sources, reason: 'source change' }, context);
    assert.equal(result, 0);
    assert.deepEqual(context.commands, [['run', 'prebuild']]);
    assert.deepEqual(
        context.projects.map(({ configFile, sources: selectedSources }) => ({ configFile, sources: selectedSources })),
        [
            { configFile: 'vitest.config.ts', sources },
            { configFile: 'config/testing/vitest.monaco.config.ts', sources },
        ],
    );
    assert.equal(fs.readFileSync(context.outputFile, 'utf8'), 'mode=affected\ncoverage=false\n');
});

test('no related specs and selection errors fall back to full validation with coverage outputs updated', async () => {
    for (const runRelatedProject of [async () => ({ selected: 0, success: true }), async () => ({ selectionError: new Error('graph could not resolve import') })]) {
        const context = executionContext({ runRelatedProject });
        assert.equal(await executePlan({ mode: 'affected', sources: [component] }, context), 0);
        assert.deepEqual(context.commands, [
            ['run', 'prebuild'],
            ['run', 'test:ci'],
        ]);
        assert.match(fs.readFileSync(context.outputFile, 'utf8'), /mode=full\ncoverage=true\n$/);
    }
});

test('a failed selected test fails the job without hiding the failure behind a full-suite retry', async () => {
    const context = executionContext({ runRelatedProject: async () => ({ selected: 1, success: false }) });
    assert.equal(await executePlan({ mode: 'affected', sources: [component] }, context), 1);
    assert.deepEqual(context.commands, [['run', 'prebuild']]);
    assert.doesNotMatch(fs.readFileSync(context.outputFile, 'utf8'), /coverage=true/);
});

test('an unaffected diff runs no prebuild and explicitly reports absent coverage', async () => {
    const context = executionContext();
    assert.equal(await executePlan({ mode: 'none', reason: 'no client changes' }, context), 0);
    assert.deepEqual(context.commands, []);
    assert.deepEqual(context.projects, []);
    assert.equal(fs.readFileSync(context.outputFile, 'utf8'), 'mode=none\ncoverage=false\n');
});

test('selected mode removes stale full-suite coverage instead of leaving a misleading report', async () => {
    const context = executionContext();
    const coverage = path.join(context.root, 'build/test-results/vitest/coverage');
    fs.mkdirSync(coverage, { recursive: true });
    fs.writeFileSync(path.join(coverage, 'coverage-summary.json'), '{"total":{"lines":{"pct":100}}}');
    await executePlan({ mode: 'affected', sources: [component] }, context);
    assert.equal(fs.existsSync(coverage), false);
});

test('Vitest graph selection executes indirect consumers and disables inherited all-files coverage', async () => {
    const { root } = fixture({
        'vitest.fixture.config.mjs': `export default { test: { globals: true, environment: 'node', include: ['**/*.spec.ts'], coverage: { enabled: true, provider: 'istanbul', include: ['**/*.ts'], thresholds: { lines: 100 } } } };`,
        'source.ts': 'export const answer = 42;',
        'consumer.ts': "export { answer } from './source';",
        'consumer.spec.ts': "import { answer } from './consumer'; test('indirect consumer', () => expect(answer).toBe(42));",
        'unrelated.spec.ts': "throw new Error('Unrelated spec was executed');",
        'uncovered.ts': 'export const uncovered = () => 123;',
    });
    const messages = [];
    const result = await runRelatedProject({ root, configFile: 'vitest.fixture.config.mjs', sources: ['source.ts'], log: (message) => messages.push(message) });
    assert.deepEqual(result, { selected: 1, success: true });
    assert.ok(messages.includes('  consumer.spec.ts'));
    assert.ok(!messages.includes('  unrelated.spec.ts'));
    assert.equal(fs.existsSync(path.join(root, 'coverage')), false);
    assert.match(fs.readFileSync(path.join(root, 'build/test-results/vitest/junit.xml'), 'utf8'), /indirect consumer/);
});

test('graph discovery restores the environment before any full-suite fallback prebuild', async () => {
    const { root } = fixture({ 'vitest.fixture.config.mjs': "export default { test: { include: ['**/*.spec.ts'] } };" });
    const before = [process.env.VITEST, process.env.TEST, process.env.NODE_ENV];
    const result = await runRelatedProject({ root, configFile: 'vitest.fixture.config.mjs', sources: ['source.ts'], log: () => {} });
    assert.deepEqual(result, { selected: 0, success: true });
    assert.deepEqual([process.env.VITEST, process.env.TEST, process.env.NODE_ENV], before);
});
