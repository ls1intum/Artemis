import { Component, computed, input } from '@angular/core';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TooltipModule } from 'primeng/tooltip';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

type TooltipPosition = 'top' | 'bottom' | 'left' | 'right';

@Component({
    selector: 'jhi-help-icon',
    template: ` <fa-icon [icon]="faQuestionCircle" class="text-muted-color" [pTooltip]="text() | artemisTranslate" [tooltipPosition]="tooltipPosition()" appendTo="body" /> `,
    imports: [FaIconComponent, TooltipModule, ArtemisTranslatePipe],
})
export class HelpIconComponent {
    protected readonly faQuestionCircle = faQuestionCircle;

    placement = input<string>('auto');
    text = input.required<string>();

    /**
     * Maps the ng-bootstrap-style `placement` value (e.g. `'auto'`, `'right auto'`) to a single
     * PrimeNG tooltip position. ng-bootstrap accepts a space-separated fallback list whose first
     * token is the primary placement; PrimeNG only supports a single side. `'auto'` (no explicit
     * side) maps to PrimeNG's default `'top'`. This keeps the public `placement` input unchanged
     * while rendering with PrimeNG.
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
