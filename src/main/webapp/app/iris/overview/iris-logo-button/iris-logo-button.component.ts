import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { of, switchMap } from 'rxjs';
import { FeatureToggle, FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { ButtonSize, ButtonType, getTumAetUiButtonOptions } from 'app/shared-ui/components/buttons/button/button.component';
import { IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { TumAetUiButtonComponent, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { IrisLogoComponent } from 'app/iris/overview/iris-logo/iris-logo.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

let nextIrisLogoButtonId = 0;

@Component({
    selector: 'jhi-iris-logo-button',
    templateUrl: './iris-logo-button.component.html',
    imports: [TumAetUiButtonComponent, TumAetUiTooltipDirective, IrisLogoComponent, TranslateDirective, ArtemisTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IrisLogoButtonComponent {
    private readonly featureToggleService = inject(FeatureToggleService);

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
    /** Same rule as `jhiFeatureToggle`: no feature means active; read here so no wrapper element carries button state. */
    private readonly featureActive = toSignal(
        toObservable(this.featureToggle).pipe(
            switchMap((feature) => {
                const features = feature ? (Array.isArray(feature) ? feature : [feature]) : [];
                return features.length ? this.featureToggleService.getFeatureTogglesActive(features) : of(true);
            }),
        ),
        { initialValue: true },
    );
    protected readonly isDisabled = computed(() => this.disabled() || this.isLoading() || !this.featureActive());

    SMALL = IrisLogoSize.SMALL;
}
