import { Component, input } from '@angular/core';
import { TumAetUiOverlayPlacement } from '@tumaet/ui-angular/overlay';
import { TumAetUiTooltipDirective } from '@tumaet/ui-angular/tooltip';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-feature-overlay',
    template: `
        <div
            class="inline-block"
            data-testid="feature-overlay-host"
            [tumAetUiTooltip]="enabled() ? undefined : ('featureOverview.overlay.title' | artemisTranslate)"
            [tumAetUiTooltipPlacement]="placement()"
        >
            <div [class.pointer-events-none]="!enabled()" [class.opacity-50]="!enabled()" data-testid="feature-overlay-content">
                <ng-content />
            </div>
        </div>
    `,
    imports: [TumAetUiTooltipDirective, ArtemisTranslatePipe],
})
export class FeatureOverlayComponent {
    enabled = input<boolean>(true);
    placement = input<TumAetUiOverlayPlacement>('left');
}
