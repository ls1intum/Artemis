import { Component, input, output } from '@angular/core';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { NgClass } from '@angular/common';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

/**
 * enum for button types
 * @enum {string}
 */
export enum ButtonType {
    DEFAULT = 'btn-default',
    PRIMARY = 'btn-primary',
    SECONDARY = 'btn-secondary',
    SUCCESS = 'btn-success',
    WARNING = 'btn-warning',
    ERROR = 'btn-danger',
    INFO = 'btn-info',

    PRIMARY_OUTLINE = 'btn-outline-primary',
    SUCCESS_OUTLINE = 'btn-outline-success',
    ERROR_OUTLINE = 'btn-outline-danger',
}

/**
 * enum for button sizes
 * @enum {string}
 */
export enum ButtonSize {
    SMALL = 'btn-sm',
    MEDIUM = 'btn-md',
    LARGE = 'btn-lg',
}

export enum TooltipPlacement {
    TOP = 'top',
    BOTTOM = 'bottom',
    LEFT = 'left',
    RIGHT = 'right',
}

/**
 * A generic button component that has a disabled & loading state.
 * The only event output is the click.
 *
 * Can be used as a button with text and/or icon.
 */
@Component({
    selector: 'jhi-button',
    templateUrl: './button.component.html',
    imports: [NgClass, NgbTooltip, FeatureToggleDirective, FaIconComponent, TranslateDirective, ArtemisTranslatePipe],
})
export class ButtonComponent {
    protected readonly faCircleNotch = faCircleNotch;

    readonly btnType = input(ButtonType.PRIMARY);
    readonly btnSize = input(ButtonSize.MEDIUM);

    /** You might need to set d-flex as well when using the button */
    fullWidth = input<boolean>(false);
    // Fa-icon name.
    readonly icon = input<IconProp>(undefined!);
    // Translation placeholders, will be translated in the component.
    readonly title = input<string>(undefined!);
    readonly tooltip = input<string>(undefined!);
    readonly tooltipPlacement = input<TooltipPlacement>(TooltipPlacement.TOP);

    readonly disabled = input(false);
    readonly isLoading = input(false);
    readonly featureToggle = input<FeatureToggle | FeatureToggle[]>(undefined!); // Disable by feature toggle.

    readonly shouldSubmit = input(true);
    readonly shouldToggle = input(false);

    readonly onClick = output<MouseEvent>();
}
