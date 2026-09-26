import { ChangeDetectionStrategy, Component, booleanAttribute, computed, input } from '@angular/core';

export type TumAetUiTagSeverity = 'secondary' | 'success' | 'info' | 'warn' | 'danger' | 'contrast';

const TAG_BASE = 'tumaet:inline-flex tumaet:items-center tumaet:gap-1 tumaet:px-2 tumaet:py-1 tumaet:text-sm';

const TAG_SEVERITY: Record<TumAetUiTagSeverity, string> = {
    secondary: 'tumaet:bg-hover-background tumaet:text-text',
    success: '',
    info: '',
    warn: '',
    danger: '',
    contrast: 'tumaet:bg-contrast-background tumaet:text-contrast',
};

@Component({
    selector: 'tumaet-ui-tag',
    templateUrl: './tumaet-ui-tag.component.html',
    styleUrl: './tumaet-ui-tag.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiTagComponent {
    readonly severity = input<TumAetUiTagSeverity>('secondary');
    readonly value = input<string>();
    readonly rounded = input(false, { transform: booleanAttribute });
    readonly bold = input(true, { transform: booleanAttribute });

    protected readonly tagClasses = computed(() =>
        `${TAG_BASE} ${this.bold() ? 'tumaet:font-bold' : 'tumaet:font-normal'} ${this.rounded() ? 'tumaet:rounded-full' : 'tumaet:rounded-md'} ${TAG_SEVERITY[this.severity()]}`
            .replace(/\s+/g, ' ')
            .trim(),
    );
}
