import { Component, computed, input } from '@angular/core';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

type TooltipPosition = 'top' | 'bottom' | 'left' | 'right';

@Component({
    selector: 'jhi-help-icon',
    template: `<span tabindex="0" [tumAetUiTooltip]="text() | artemisTranslate" [tumAetUiTooltipPlacement]="tooltipPosition()"
        ><fa-icon [icon]="faQuestionCircle" class="text-muted-color"
    /></span>`,
    imports: [FaIconComponent, TumAetUiTooltipDirective, ArtemisTranslatePipe],
})
export class HelpIconComponent {
    protected readonly faQuestionCircle = faQuestionCircle;

    placement = input<string>('auto');
    text = input.required<string>();

    /**
     * Resolves the preferred side from existing placement values. TUM UI flips the overlay when
     * that side does not fit in the viewport.
     */
    protected readonly tooltipPosition = computed<TooltipPosition>(() => {
        const primary = this.placement().trim().split(/\s+/)[0];
        switch (primary) {
            case 'top':
            case 'bottom':
            case 'left':
            case 'right':
                return primary;
            default:
                return 'top';
        }
    });
}
