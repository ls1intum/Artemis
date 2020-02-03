import { Component, Input } from '@angular/core';
import { FeatureToggle } from 'app/feature-toggle';

@Component({
    selector: 'jhi-ide-button',
    templateUrl: './intellij-button.component.html',
    styleUrls: ['./intellij-button.component.scss'],
})
export class IntellijButtonComponent {
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
