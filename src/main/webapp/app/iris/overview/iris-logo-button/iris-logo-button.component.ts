import { ChangeDetectionStrategy, Component, computed, input, output, viewChild } from '@angular/core';
import { FeatureToggle } from 'app/foundation/feature-toggle/feature-toggle.service';
import { ButtonSize, ButtonType, getTumAetUiButtonOptions } from 'app/shared-ui/components/buttons/button/button.component';
import { IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { TumAetUiButtonComponent, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { FeatureToggleDirective } from 'app/foundation/feature-toggle/feature-toggle.directive';
import { IrisLogoComponent } from 'app/iris/overview/iris-logo/iris-logo.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

let nextIrisLogoButtonId = 0;

@Component({
    selector: 'jhi-iris-logo-button',
    templateUrl: './iris-logo-button.component.html',
    imports: [TumAetUiButtonComponent, TumAetUiTooltipDirective, FeatureToggleDirective, IrisLogoComponent, TranslateDirective, ArtemisTranslatePipe],
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

    protected readonly buttonOptions = computed(() => getTumAetUiButtonOptions(this.btnType(), this.btnSize()));

    protected readonly tooltipId = `iris-logo-button-tooltip-${nextIrisLogoButtonId++}`;
    protected readonly featureToggleControl = viewChild(FeatureToggleDirective);

    SMALL = IrisLogoSize.SMALL;
}
