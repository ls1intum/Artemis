import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';
import { IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { NgClass } from '@angular/common';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IrisLogoComponent } from 'app/iris/overview/iris-logo/iris-logo.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-iris-logo-button',
    templateUrl: './iris-logo-button.component.html',
    imports: [NgClass, NgbTooltip, FeatureToggleDirective, FaIconComponent, IrisLogoComponent, TranslateDirective, ArtemisTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IrisLogoButtonComponent {
    readonly btnType = input<ButtonType>(ButtonType.PRIMARY);
    readonly btnSize = input<ButtonSize>(ButtonSize.MEDIUM);
    // Translation placeholders, will be translated in the component.
    readonly title = input<string>();
    readonly tooltip = input<string>();

    readonly disabled = input<boolean>(false);
    readonly isLoading = input<boolean>(false);
    readonly featureToggle = input<FeatureToggle | FeatureToggle[]>(); // Disable by feature toggle.

    readonly shouldSubmit = input<boolean>(true);

    readonly onClick = output<MouseEvent>();

    // Icons
    faCircleNotch = faCircleNotch;
    SMALL = IrisLogoSize.SMALL;
}
