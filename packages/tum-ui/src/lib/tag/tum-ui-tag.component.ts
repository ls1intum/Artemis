import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type TumUiTagSeverity = 'secondary' | 'success' | 'info' | 'warn' | 'danger' | 'contrast';

const TAG_BASE = 'tum:inline-flex tum:items-center tum:gap-1 tum:px-2 tum:py-1 tum:text-sm tum:font-bold';

const TAG_SEVERITY: Record<TumUiTagSeverity, string> = {
    secondary: 'tum:bg-tum-ui-surface-200 tum:text-tum-ui-surface-700 tum:dark:bg-tum-ui-surface-800 tum:dark:text-tum-ui-surface-200',
    success: '',
    info: '',
    warn: '',
    danger: '',
    contrast: 'tum:bg-tum-ui-surface-900 tum:text-tum-ui-surface-0 tum:dark:bg-tum-ui-surface-0 tum:dark:text-tum-ui-surface-900',
};

@Component({
    selector: 'tum-ui-tag',
    templateUrl: './tum-ui-tag.component.html',
    styleUrl: './tum-ui-tag.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiTagComponent {
    readonly severity = input<TumUiTagSeverity>('secondary');
    readonly value = input<string>();
    readonly rounded = input(false);

    readonly styleClass = input<string>('');

    protected readonly tagClasses = computed(() =>
        `${TAG_BASE} ${this.rounded() ? 'tum:rounded-full' : 'tum:rounded-md'} ${TAG_SEVERITY[this.severity()]} ${this.styleClass()}`.replace(/\s+/g, ' ').trim(),
    );
}
