import { describe, it } from 'vitest';
import rule from './require-chart-accessible-name.mjs';
import { createTemplateRuleTester, createTypeScriptRuleTester } from './rule-tester.mjs';

const templateRuleTester = createTemplateRuleTester();
const typeScriptRuleTester = createTypeScriptRuleTester();

describe('require-chart-accessible-name', () => {
    it('requires an accessible name or an explicit aria-hidden on every p-chart in a template file', () => {
        templateRuleTester.run('require-chart-accessible-name', rule, {
            valid: [
                // Bound ariaLabel — the preferred form, reusing the chart's visible heading key.
                { code: `<p-chart type="bar" [data]="chartData()" [ariaLabel]="'artemisApp.courseStatistics.activeStudents' | artemisTranslate" />` },
                // A static ariaLabel is equally acceptable.
                { code: '<p-chart type="pie" ariaLabel="Submissions per state" />' },
                // Naming from the visible heading via its id.
                { code: `<p-chart type="line" [ariaLabelledBy]="'active-students-heading'" />` },
                { code: '<p-chart type="line" ariaLabelledBy="active-students-heading" />' },
                // Decorative chart: the figure is also rendered as text next to it.
                { code: '<p-chart type="doughnut" aria-hidden="true" />' },
                // Other elements are out of scope.
                { code: '<canvas></canvas>' },
                { code: '<div class="chart"></div>' },
            ],
            invalid: [
                { code: '<p-chart type="bar" [data]="chartData()" [options]="chartOptions()" />', errors: [{ messageId: 'missingAccessibleName' }] },
                // A title/tooltip is not an accessible name for role="img".
                { code: '<p-chart type="doughnut" pTooltip="Score" />', errors: [{ messageId: 'missingAccessibleName' }] },
                // aria-hidden="false" is the opposite of hiding the chart.
                { code: '<p-chart type="bar" aria-hidden="false" />', errors: [{ messageId: 'missingAccessibleName' }] },
                // An empty or nullish name yields no accessible name at all.
                { code: '<p-chart type="bar" ariaLabel="" />', errors: [{ messageId: 'missingAccessibleName' }] },
                { code: `<p-chart type="bar" [ariaLabel]="''" />`, errors: [{ messageId: 'missingAccessibleName' }] },
                { code: '<p-chart type="bar" [ariaLabel]="undefined" />', errors: [{ messageId: 'missingAccessibleName' }] },
                { code: '<p-chart type="bar" ariaLabelledBy="  " />', errors: [{ messageId: 'missingAccessibleName' }] },
                // Nested charts are each reported.
                {
                    code: '<div><p-chart type="bar" /><p-chart type="line" /></div>',
                    errors: [{ messageId: 'missingAccessibleName' }, { messageId: 'missingAccessibleName' }],
                },
            ],
        });
    });

    it('also covers inline template strings in component files', () => {
        typeScriptRuleTester.run('require-chart-accessible-name', rule, {
            valid: [
                {
                    code: "@Component({ template: `<p-chart type=\"bar\" [ariaLabel]=\"'some.key' | artemisTranslate\" />` }) class C {}",
                },
                { code: '@Component({ template: `<p-chart type="doughnut" aria-hidden="true" />` }) class C {}' },
                // An attribute value containing `>` must not truncate the tag scan.
                { code: '@Component({ template: `<p-chart [options]="a > b" ariaLabel="Scores" />` }) class C {}' },
                // No chart at all.
                { code: '@Component({ template: `<div class="chart"></div>` }) class C {}' },
                // A `template` property that is not a string is ignored.
                { code: '@Component({ template: someTemplate }) class C {}' },
            ],
            invalid: [
                { code: '@Component({ template: `<p-chart type="bar" [data]="chartData()" />` }) class C {}', errors: [{ messageId: 'missingAccessibleName' }] },
                { code: '@Component({ template: `<p-chart aria-hidden="false" />` }) class C {}', errors: [{ messageId: 'missingAccessibleName' }] },
                // `>` inside a binding must not hide the missing label either.
                { code: '@Component({ template: `<p-chart [options]="a > b" />` }) class C {}', errors: [{ messageId: 'missingAccessibleName' }] },
                {
                    code: '@Component({ template: `<p-chart type="bar" />\\n<p-chart type="line" />` }) class C {}',
                    errors: [{ messageId: 'missingAccessibleName' }, { messageId: 'missingAccessibleName' }],
                },
            ],
        });
    });
});
