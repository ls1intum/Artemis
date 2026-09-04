import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TumUiOverlayPlacement } from '../overlay/tum-ui-overlay.service';

const ARROW_BASE = 'tum:absolute tum:h-2 tum:w-2 tum:rotate-45 tum:bg-tooltip-background';

const ARROW_POSITION: Record<TumUiOverlayPlacement, string> = {
    top: 'tum:left-1/2 tum:top-full tum:-translate-x-1/2 tum:-translate-y-1/2',
    bottom: 'tum:left-1/2 tum:bottom-full tum:-translate-x-1/2 tum:translate-y-1/2',
    left: 'tum:top-1/2 tum:left-full tum:-translate-y-1/2 tum:-translate-x-1/2',
    right: 'tum:top-1/2 tum:right-full tum:-translate-y-1/2 tum:translate-x-1/2',
};

@Component({
    selector: 'tum-ui-tooltip-content',
    template: `{{ text() }}<span aria-hidden="true" [class]="arrowClasses()"></span>`,
    host: {
        '[attr.data-slot]': '"tooltip-content"',
        role: 'tooltip',
        '[attr.id]': 'id()',
        class: 'tum-ui-tooltip-bubble tum:relative tum:inline-block tum:max-w-50 tum:rounded-md tum:bg-tooltip-background tum:px-3 tum:py-2 tum:text-sm tum:text-tooltip tum:shadow-md',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiTooltipContentComponent {
    readonly text = input<string>('');
    readonly id = input<string>('');
    readonly placement = input<TumUiOverlayPlacement>('top');

    protected readonly arrowClasses = computed(() => `${ARROW_BASE} ${ARROW_POSITION[this.placement()]}`);
}
