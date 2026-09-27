import { describe, it } from 'vitest';
import rule from './require-chart-accessible-name.mjs';
import { createTemplateRuleTester, createTypeScriptRuleTester } from './rule-tester.mjs';

const templateRuleTester = createTemplateRuleTester();
const typeScriptRuleTester = createTypeScriptRuleTester();

describe('require-chart-accessible-name', () => {
    it('requires an accessible name or an explicit aria-hidden on every chart in a template file', () => {
        templateRuleTester.run('require-chart-accessible-name', rule, {
            valid: [
                // Bound ariaLabel — the preferred form, reusing the chart's visible heading key.
                { code: `<tumaet-ui-bar-chart [series]="chartData().series" [ariaLabel]="'artemisApp.courseStatistics.activeStudents' | artemisTranslate" />` },
                // A static ariaLabel is equally acceptable.
                { code: '<tumaet-ui-doughnut-chart ariaLabel="Submissions per state" />' },
                // Naming from the visible heading via its id.
                { code: `<tumaet-ui-line-chart [ariaLabelledBy]="'active-students-heading'" />` },
                { code: '<tumaet-ui-line-chart ariaLabelledBy="active-students-heading" />' },
                // Decorative chart: the figure is also rendered as text next to it.
                { code: '<tumaet-ui-doughnut-chart aria-hidden="true" />' },
                // Other elements are out of scope.
                { code: '<canvas></canvas>' },
                { code: '<div class="chart"></div>' },
            ],
            invalid: [
                { code: '<tumaet-ui-bar-chart [labels]="chartData().labels" [series]="chartData().series" />', errors: [{ messageId: 'missingAccessibleName' }] },
                // A title/tooltip is not an accessible name for role="img".
                { code: '<tumaet-ui-doughnut-chart pTooltip="Score" />', errors: [{ messageId: 'missingAccessibleName' }] },
                // aria-hidden="false" is the opposite of hiding the chart, and a bare aria-hidden is invalid ARIA.
                { code: '<tumaet-ui-bar-chart aria-hidden="false" />', errors: [{ messageId: 'missingAccessibleName' }] },
                { code: '<tumaet-ui-bar-chart aria-hidden="" />', errors: [{ messageId: 'missingAccessibleName' }] },
                // An empty or nullish name yields no accessible name at all.
                { code: '<tumaet-ui-bar-chart ariaLabel="" />', errors: [{ messageId: 'missingAccessibleName' }] },
                { code: `<tumaet-ui-bar-chart [ariaLabel]="''" />`, errors: [{ messageId: 'missingAccessibleName' }] },
                { code: '<tumaet-ui-bar-chart [ariaLabel]="undefined" />', errors: [{ messageId: 'missingAccessibleName' }] },
                { code: '<tumaet-ui-bar-chart ariaLabelledBy="  " />', errors: [{ messageId: 'missingAccessibleName' }] },
                // Nested charts are each reported.
                {
                    code: '<div><tumaet-ui-bar-chart /><tumaet-ui-line-chart /></div>',
                    errors: [{ messageId: 'missingAccessibleName' }, { messageId: 'missingAccessibleName' }],
                },
            ],
        });
    });

    it('also covers inline template strings in component files', () => {
        typeScriptRuleTester.run('require-chart-accessible-name', rule, {
            valid: [
                {
                    code: '@Component({ template: `<tumaet-ui-bar-chart type="bar" [ariaLabel]="\'some.key\' | artemisTranslate" />` }) class C {}',
                },
                { code: '@Component({ template: `<tumaet-ui-doughnut-chart aria-hidden="true" />` }) class C {}' },
                // An attribute value containing `>` must not truncate the tag scan.
                { code: '@Component({ template: `<tumaet-ui-bar-chart [options]="a > b" ariaLabel="Scores" />` }) class C {}' },
                // No chart at all.
                { code: '@Component({ template: `<div class="chart"></div>` }) class C {}' },
                // A `template` property that is not a string is ignored.
                { code: '@Component({ template: someTemplate }) class C {}' },
            ],
            invalid: [
                { code: '@Component({ template: `<tumaet-ui-bar-chart [series]="chartData().series" />` }) class C {}', errors: [{ messageId: 'missingAccessibleName' }] },
                { code: '@Component({ template: `<tumaet-ui-bar-chart aria-hidden="false" />` }) class C {}', errors: [{ messageId: 'missingAccessibleName' }] },
                // A name-like string nested inside another attribute's value is not an accessible name.
                {
                    code: `@Component({ template: \`<tumaet-ui-bar-chart pTooltip='[ariaLabel]="Scores"' />\` }) class C {}`,
                    errors: [{ messageId: 'missingAccessibleName' }],
                },
                // `>` inside a binding must not hide the missing label either.
                { code: '@Component({ template: `<tumaet-ui-bar-chart [options]="a > b" />` }) class C {}', errors: [{ messageId: 'missingAccessibleName' }] },
                {
                    code: '@Component({ template: `<tumaet-ui-bar-chart />\\n<tumaet-ui-line-chart />` }) class C {}',
                    errors: [{ messageId: 'missingAccessibleName' }, { messageId: 'missingAccessibleName' }],
                },
            ],
        });
    });
});
