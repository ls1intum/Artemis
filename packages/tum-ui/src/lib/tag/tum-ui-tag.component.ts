import { ChangeDetectionStrategy, Component, booleanAttribute, computed, input } from '@angular/core';

export type TumUiTagSeverity = 'secondary' | 'success' | 'info' | 'warn' | 'danger' | 'contrast';

const TAG_BASE = 'tum:inline-flex tum:items-center tum:gap-1 tum:px-2 tum:py-1 tum:text-sm';

const TAG_SEVERITY: Record<TumUiTagSeverity, string> = {
    secondary: 'tum:bg-hover-background tum:text-text',
    success: '',
    info: '',
    warn: '',
    danger: '',
    contrast: 'tum:bg-contrast-background tum:text-contrast',
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
    readonly rounded = input(false, { transform: booleanAttribute });
    readonly bold = input(true, { transform: booleanAttribute });

    protected readonly tagClasses = computed(() =>
        `${TAG_BASE} ${this.bold() ? 'tum:font-bold' : 'tum:font-normal'} ${this.rounded() ? 'tum:rounded-full' : 'tum:rounded-md'} ${TAG_SEVERITY[this.severity()]}`
            .replace(/\s+/g, ' ')
            .trim(),
    );
}
