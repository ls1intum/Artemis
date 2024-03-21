import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';
import { IrisLogoSize } from 'app/iris/iris-logo/iris-logo.component';

@Component({
    selector: 'jhi-iris-logo-button',
    templateUrl: './iris-logo-button.component.html',
})
export class IrisLogoButtonComponent {
    @Input() btnType = ButtonType.PRIMARY;
    @Input() btnSize = ButtonSize.MEDIUM;
    // Translation placeholders, will be translated in the component.
    @Input() title: string;
    @Input() tooltip: string;

    @Input() disabled = false;
    @Input() isLoading = false;
    @Input() featureToggle: FeatureToggle | FeatureToggle[]; // Disable by feature toggle.

    @Input() shouldSubmit = true;

    @Output() onClick = new EventEmitter<MouseEvent>();

    // Icons
    faCircleNotch = faCircleNotch;
    SMALL = IrisLogoSize.SMALL;
}
