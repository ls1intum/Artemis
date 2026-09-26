import { ChangeDetectionStrategy, Component, booleanAttribute, computed, input } from '@angular/core';

export type TumAetUiTagSeverity = 'secondary' | 'success' | 'info' | 'warn' | 'danger' | 'contrast';

/**
 * `md` is the standalone tag. `sm` is for a tag that annotates something else, such as a row of status
 * markers above a card, where the default weight competes with the content it is describing.
 */
export type TumAetUiTagSize = 'sm' | 'md';

const TAG_BASE = 'tumaet:inline-flex tumaet:items-center tumaet:gap-1';

const TAG_SIZE: Record<TumAetUiTagSize, string> = {
    sm: 'tumaet:px-2 tumaet:py-0.5 tumaet:text-xs tumaet:font-semibold',
    md: 'tumaet:px-2 tumaet:py-1 tumaet:text-sm tumaet:font-bold',
};

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
    readonly size = input<TumAetUiTagSize>('md');
    readonly value = input<string>();
    readonly rounded = input(false, { transform: booleanAttribute });

    protected readonly tagClasses = computed(() =>
        `${TAG_BASE} ${TAG_SIZE[this.size()]} ${this.rounded() ? 'tumaet:rounded-full' : 'tumaet:rounded-md'} ${TAG_SEVERITY[this.severity()]}`.replace(/\s+/g, ' ').trim(),
    );
}
