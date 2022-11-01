import { Component, EventEmitter, Input, Output } from '@angular/core';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';
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
    // Disable by feature toggle.
    @Input() featureToggle: FeatureToggle = FeatureToggle.ProgrammingExercises;
    // Indirect handler to disable clicking while loading
    @Output() clickHandler = new EventEmitter<void>();

    // Icons
    faCircleNotch = faCircleNotch;

    constructor() {}

    public get btnPrimary(): boolean {
        return !this.outlined;
    }

    /**
     * Forwards the click event to the handler only if the button is enabled
     */
    public handleClick() {
        if (!this.buttonLoading) {
            this.clickHandler.emit();
        }
    }
}
