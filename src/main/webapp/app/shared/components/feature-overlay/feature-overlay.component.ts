import { Component, input } from '@angular/core';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-feature-overlay',
    template: `
        <div ngbTooltip="{{ enabled() ? null : ('artemisApp.featureToggles.title' | artemisTranslate) }}" placement="left">
            <div [class.disabled]="!enabled()">
                <ng-content></ng-content>
            </div>
        </div>
    `,
    styles: [
        `
            .disabled {
                pointer-events: none;
                opacity: 0.5;
            }
        `,
    ],
    imports: [NgbTooltip, ArtemisTranslatePipe],
})
export class FeatureOverlayComponent {
    enabled = input<boolean>(true);
}
