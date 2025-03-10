import { NgClass } from '@angular/common';
import { Component, input } from '@angular/core';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-feature-overlay',
    template: `
        <div ngbTooltip="{{ enabled() ? null : ('artemisApp.featureToggles.title' | artemisTranslate) }}" placement="left">
            <div [class.disabled]="!enabled()">
                <div [ngClass]="{ 'pe-none': !enabled(), 'opacity-50': !enabled() }">
                    <ng-content></ng-content>
                </div>
            </div>
        </div>
    `,
    imports: [NgbTooltip, NgClass, ArtemisTranslatePipe],
})
export class FeatureOverlayComponent {
    enabled = input<boolean>(true);
}
