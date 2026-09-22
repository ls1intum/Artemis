import { describe, it } from 'vitest';
import rule from './tum-ui-no-restyle.mjs';
import { createTemplateRuleTester } from './rule-tester.mjs';

const tester = createTemplateRuleTester();
const error = (messageId) => ({ messageId });

describe('tum-ui-no-restyle', () => {
    it('checks Angular class and style syntax without evaluating conditions or layout values', () => {
        tester.run('tum-ui-no-restyle', rule, {
            valid: [
                '<button tumUiButton size="small" severity="danger" class="mt-4 w-full"></button>',
                '<tum-ui-panel class="min-w-0 overflow-hidden h-[300px]" />',
                '<div class="p-4 bg-primary rounded-md"></div>',
                '<button [variant]="\'p-4\'"></button>',
                '<tum-ui-button [class.w-full]="wide()" />',
                "<tum-ui-button [class]=\"wide() ? 'w-full' : 'w-auto'\" />",
                '<tum-ui-button [ngClass]="{\'w-full\': condition()}" />',
                "<tum-ui-button [class]=\"['mt-4', wide() ? 'w-full' : '']\" />",
                '<tum-ui-button [class]="wide() && \'w-full\'" />',
                "<tum-ui-button class=\"mt-2 {{ wide() ? 'w-full' : 'w-auto' }}\" />",
                '<tum-ui-button [style.width.px]="width()" />',
                '<tum-ui-button [ngStyle]="{\'width.px\': width()}" />',
                '<tum-ui-button style="width: calc(100% - 2rem); --tumaet-ui-primary-color: var(--primary)" />',
                '<tum-ui-button class="text-center truncate md:w-[calc(100%-1rem)]" />',
                '<tum-ui-button class="[--tumaet-ui-primary-color:var(--primary)]" />',
                '<div [class]="classes()"></div>',
                '<tum-ui-button [attr.aria-label]="\'bg-primary\'" />',
            ],
            invalid: [
                {
                    code: '<input tumUiInput class="p-4" />',
                    errors: [{ messageId: 'appearance', data: { value: 'p-4', component: 'tumUiInput', guidance: 'Use tumUiInputSize and tumUiInputInvalid.' } }],
                },
                { code: '<textarea tumUiTextarea class="font-bold"></textarea>', errors: [error('appearance')] },
                { code: '<div class="tum-ui-btn"></div>', errors: [error('internal')] },
                { code: '<div class="[&_[class*=tum-ui-panel]]:hidden"></div>', errors: [error('internal')] },
                { code: '<tum-ui-button class="-tracking-widest" />', errors: [error('appearance')] },
                { code: "<tum-ui-button [class]=\"flag() && 'p-4' || 'm-4'\" />", errors: [error('appearance')] },
                ...[
                    'p-4',
                    'hover:bg-primary',
                    'md:!rounded-full',
                    'tum:text-sm!',
                    'border-0',
                    'font-bold',
                    'italic',
                    'uppercase',
                    'underline',
                    'tabular-nums',
                    'opacity-0',
                    'pbs-4',
                    'pbe-4',
                    'shadow-none',
                    'bg-[url(https://example.com/a.png)]',
                    '[padding:1rem]',
                    'hover:[color:red]',
                ].map((cls) => ({ code: `<button tumUiButton class="${cls}"></button>`, errors: [error('appearance')] })),
                { code: '<tum-ui-button [class.p-4]="active()" />', errors: [error('appearance')] },
                { code: '<tum-ui-button [attr.class]="\'p-4\'" />', errors: [error('appearance')] },
                { code: '<tum-ui-button [ngClass]="{\'p-4\': active()}" />', errors: [error('appearance')] },
                { code: "<tum-ui-button [class]=\"active() ? 'p-4' : 'm-4'\" />", errors: [error('appearance')] },
                { code: '<tum-ui-button [class]="classes()" />', errors: [error('opaque')] },
                { code: '<tum-ui-button [class]="\'bg-\' + color()" />', errors: [error('opaque')] },
                { code: '<tum-ui-button class="bg-{{color()}}" />', errors: [error('opaque')] },
                { code: '<tum-ui-button [ngClass]="[classes(), moreClasses()]" />', errors: [error('opaque')] },
                { code: '<tum-ui-button [style.padding.px]="padding()" />', errors: [error('style')] },
                { code: '<tum-ui-button [ngStyle]="{backgroundColor: color()}" />', errors: [error('style')] },
                { code: '<tum-ui-button [ngStyle]="styles()" />', errors: [error('style')] },
                { code: '<tum-ui-button style="background: var(--primary)" />', errors: [error('style')] },
                { code: '<tum-ui-button [attr.style]="\'padding: 1rem\'" />', errors: [error('style')] },
                { code: '<tum-ui-tag styleClass="font-normal!" />', errors: [error('legacy')] },
                { code: '<tum-ui-message [styleClass]="classes()" />', errors: [error('legacy')] },
                { code: '<tum-ui-panel class="[&_.tum-ui-panel-header]:hidden" />', errors: [error('internal')] },
                { code: '<div class="[&_.tum-ui-panel-header]:hidden"></div>', errors: [error('internal')] },
            ],
        });
    });
});
