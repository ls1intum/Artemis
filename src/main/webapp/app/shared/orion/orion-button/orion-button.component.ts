import { Component, Input } from '@angular/core';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';

@Component({
    selector: 'jhi-ide-button',
    templateUrl: './orion-button.component.html',
    styleUrls: ['./orion-button.component.scss'],
})
export class OrionButtonComponent {
    @Input() buttonLabel: string;
    @Input() buttonLoading = false;
    @Input() outlined = false;
    @Input() smallButton = false;
    @Input() disabled = false;
    @Input() featureToggle = FeatureToggle; // Disable by feature toggle.

    constructor() {}

    public get btnPrimary(): boolean {
        return !this.outlined;
    }
}
