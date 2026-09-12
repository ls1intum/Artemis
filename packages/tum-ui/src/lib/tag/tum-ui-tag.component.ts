import { ChangeDetectionStrategy, Component, booleanAttribute, computed, input } from '@angular/core';

export type TumUiTagSeverity = 'secondary' | 'success' | 'info' | 'warn' | 'danger' | 'contrast';

/**
 * `md` is the standalone tag. `sm` is for a tag that annotates something else, such as a row of status
 * markers above a card, where the default weight competes with the content it is describing.
 */
export type TumUiTagSize = 'sm' | 'md';

const TAG_BASE = 'tum:inline-flex tum:items-center tum:gap-1';

const TAG_SIZE: Record<TumUiTagSize, string> = {
    sm: 'tum:px-2 tum:py-0.5 tum:text-xs tum:font-semibold',
    md: 'tum:px-2 tum:py-1 tum:text-sm tum:font-bold',
};

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
    readonly size = input<TumUiTagSize>('md');
    readonly value = input<string>();
    readonly rounded = input(false, { transform: booleanAttribute });

    protected readonly tagClasses = computed(() =>
        `${TAG_BASE} ${TAG_SIZE[this.size()]} ${this.rounded() ? 'tum:rounded-full' : 'tum:rounded-md'} ${TAG_SEVERITY[this.severity()]}`.replace(/\s+/g, ' ').trim(),
    );
}
