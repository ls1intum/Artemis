import { Component, input } from '@angular/core';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-feature-overlay',
    template: `
        <div
            ngbTooltip="{{ enabled() ? null : 'This feature is not enabled. Ask your Artemis administrator or consult the documentation for more information.' }}"
            placement="left"
        >
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
    imports: [NgbTooltip],
})
export class FeatureOverlayComponent {
    enabled = input<boolean>(true);
}
