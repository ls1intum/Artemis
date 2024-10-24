import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/iris-logo/iris-logo.component';
import { CommonModule } from '@angular/common';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { NgbTooltipMocksModule } from '../../../../../test/javascript/spec/helpers/mocks/directive/ngbTooltipMocks.module';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';

@Component({
    selector: 'jhi-iris-logo-button',
    templateUrl: './iris-logo-button.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    standalone: true,
    imports: [CommonModule, FontAwesomeModule, IrisLogoComponent, ArtemisSharedModule, NgbTooltipMocksModule, FeatureToggleModule],
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
