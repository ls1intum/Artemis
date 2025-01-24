import { Component, EventEmitter, Input, Output } from '@angular/core';
import { faCircleNotch, faDownload, faRotateRight, faUpload } from '@fortawesome/free-solid-svg-icons';

import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { FeatureToggleDirective } from '../../feature-toggle/feature-toggle.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

export enum OrionButtonType {
    Other = 'Other',
    Submit = 'Submit',
    Reload = 'Reload',
    Download = 'Download',
}

@Component({
    selector: 'jhi-ide-button',
    templateUrl: './orion-button.component.html',
    imports: [FeatureToggleDirective, FaIconComponent],
})
export class OrionButtonComponent {
    @Input() buttonLabel: string;
    @Input() buttonType: OrionButtonType = OrionButtonType.Other;
    @Input() buttonLoading = false;
    @Input() outlined = false;
    @Input() smallButton = false;
    @Input() disabled = false;
    // Disable by feature toggle.
    @Input() featureToggle: FeatureToggle = FeatureToggle.ProgrammingExercises;
    // Indirect handler to disable clicking while loading
    @Output() clickHandler = new EventEmitter<void>();

    protected readonly OrionButtonType = OrionButtonType;

    // Icons
    faCircleNotch = faCircleNotch;
    faRotateRight = faRotateRight;
    faUpload = faUpload;
    faDownload = faDownload;

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
