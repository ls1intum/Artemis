import { URL } from 'node:url';
import { readFileSync } from 'node:fs';
import { parse as parseYaml } from 'yaml';
import { describe, expect, it } from 'vitest';
import stylelint from 'stylelint';
import { ESLint } from 'eslint';
import angular from 'angular-eslint';
import tsParser from '@typescript-eslint/parser';
import { tumUiDesignSystem, tumUiDesignSystemRules } from './tum-ui-design-system.mjs';

const consumer = 'src/main/webapp/app/example/example.component.scss';
const packageFile = 'packages/tum-ui/src/lib/button/tum-ui-button.directive.scss';

async function lintCss(code, filename = consumer) {
    const config = await stylelint.resolveConfig(filename);
    // Exercise the real scoped policy, without unrelated formatting rules.
    const rules = Object.fromEntries(
        ['design-system/no-private-classes', 'design-system/no-restyle'].filter((name) => config.rules[name]).map((name) => [name, config.rules[name]]),
    );
    return stylelint.lint({ code, codeFilename: filename, config: { customSyntax: 'postcss-scss', plugins: config.plugins, rules } });
}

describe('TUM UI consumer policy wiring', () => {
    it('runs consumer and package CSS checks in CI even for application-only changes', () => {
        const { scripts } = JSON.parse(readFileSync(new URL('../package.json', import.meta.url), 'utf8'));
        expect(scripts.stylelint).toContain('src/main/webapp/**/*.{css,scss}');
        expect(scripts.stylelint).toContain('pnpm run tum-ui:stylelint');
        const workflow = parseYaml(readFileSync(new URL('../.github/workflows/ci-quality.yml', import.meta.url), 'utf8'));
        const step = workflow.jobs['client-style'].steps.find((candidate) => candidate.run === 'pnpm run stylelint');
        expect(step).toBeDefined();
        expect(step.if).not.toContain('has_tum_ui');
    });

    it.each([
        '.tum-ui-panel-header { padding: 0; }',
        ':host { ::ng-deep .tum-ui-panel-title { font-weight: normal; } }',
        '[class*="tum-ui-panel-"] { display: none; }',
        'tum-ui-button { color: red; }',
        'tum-ui-button.special { padding: 100px; }',
        'button[tumUiButton] { opacity: 0; }',
        'tum-ui-tag { text-transform: uppercase; }',
        'button[tumUiButton] { text-decoration: underline; }',
        'tum-ui-button { filter: blur(4px); }',
        'button[tumUiButton].special#save:hover { padding: 100px; }',
        'tum-ui-button:is(:hover, :focus) { padding: 100px; }',
        'tum-ui-button.special, .other { padding: 100px; }',
        '.other, button[tumUiButton].special { padding: 100px; }',
        'button[tumUiButton] { border-radius: 0; }',
        'tum-ui-panel:hover { background: red; }',
        'input[tumUiInput] { font-size: 8px; }',
        'textarea[tumUiTextarea] { all: unset; }',
    ])('rejects consumer CSS overrides: %s', async (code) => {
        const result = await lintCss(code);
        expect(result.errored).toBe(true);
        expect(result.results[0].invalidOptionWarnings).toEqual([]);
    });

    it.each([
        'tum-ui-button { width: 100%; margin-inline-start: 1rem; }',
        'button[tumUiButton] { max-width: 100%; }',
        '.page-layout { display: grid; gap: 1rem; }',
        '.not-a-tum-ui-button { color: red; }',
        'a:not(.btn, [tumUiButton], .tab-link):hover { text-decoration: none; }',
        'a:not([tumUiButton]) { padding: 1rem; }',
        'tum-ui-panel .projected-content { padding: 1rem; }',
        'tum-ui-panel.special > .projected-content { padding: 1rem; }',
        'tum-ui-panel:is(:hover, :focus) .projected-content { padding: 1rem; }',
        'button[tumUiButton].special + .sibling { padding: 1rem; }',
        'tum-ui-panel { --tumaet-ui-primary-color: var(--primary); }',
    ])('allows consumer layout and theming: %s', async (code) => {
        expect((await lintCss(code)).errored).toBe(false);
    });

    it.each(['src/main/webapp/content/scss/global.scss', 'src/main/webapp/themes.css'])('protects global consumer styles outside app/: %s', async (filename) => {
        expect((await lintCss('tum-ui-button { padding: 10px; }', filename)).errored).toBe(true);
        expect((await lintCss('.tum-ui-btn { display: none; }', filename)).errored).toBe(true);
    });

    it('lets the package implement its own appearance', async () => {
        // No consumer-only rules must leak into the package config.
        const config = await stylelint.resolveConfig(packageFile);
        expect(config.rules['design-system/no-private-classes']).toBeUndefined();
        expect(config.rules['design-system/no-restyle']).toBeUndefined();
    });

    it('enables the Angular processor and template rule for consumers, not package implementations', async () => {
        const eslint = new ESLint();
        const ts = await eslint.calculateConfigForFile('src/main/webapp/app/example/example.component.ts');
        expect(ts.processor).toBe(angular.processInlineTemplates);
        const html = await eslint.calculateConfigForFile('src/main/webapp/app/example/example.component.html');
        expect(html.rules['design-system/no-restyle'][0]).toBe(2);
        const kit = await eslint.calculateConfigForFile('packages/tum-ui/src/lib/button/tum-ui-button.component.html');
        expect(kit.rules['design-system/no-restyle']).toBeUndefined();
    });

    it('reports inline templates at the original TypeScript location using the framework processor', async () => {
        const eslint = new ESLint({
            overrideConfigFile: true,
            overrideConfig: [
                { files: ['**/*.ts'], languageOptions: { parser: tsParser }, processor: angular.processInlineTemplates },
                {
                    files: ['**/*.html'],
                    languageOptions: { parser: angular.templateParser },
                    plugins: { 'design-system': tumUiDesignSystem },
                    rules: tumUiDesignSystemRules,
                },
            ],
        });
        const code = `import { Component } from '@angular/core';
@Component({
    template: '<button tumUiButton class="p-4">Save</button>',
})
export class ExampleComponent {}`;
        const [result] = await eslint.lintText(code, { filePath: 'example.component.ts' });
        expect(result.messages).toHaveLength(1);
        expect(result.messages[0]).toMatchObject({ ruleId: 'design-system/no-restyle', messageId: 'spacingClassWithSizes', line: 3 });
    });
});
