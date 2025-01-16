import { Component, EventEmitter, Input, Output } from '@angular/core';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { NgClass } from '@angular/common';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FeatureToggleDirective } from '../feature-toggle/feature-toggle.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from '../language/translate.directive';
import { ArtemisTranslatePipe } from '../pipes/artemis-translate.pipe';

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
    @Input() btnType = ButtonType.PRIMARY;
    @Input() btnSize = ButtonSize.MEDIUM;
    // Fa-icon name.
    @Input() icon: IconProp;
    // Translation placeholders, will be translated in the component.
    @Input() title: string;
    @Input() tooltip: string;
    @Input() tooltipPlacement: TooltipPlacement = TooltipPlacement.TOP;

    @Input() disabled = false;
    @Input() isLoading = false;
    @Input() featureToggle: FeatureToggle | FeatureToggle[]; // Disable by feature toggle.

    @Input() shouldSubmit = true;
    @Input() shouldToggle = false;

    @Output() onClick = new EventEmitter<MouseEvent>();

    // Icons
    readonly faCircleNotch = faCircleNotch;
}
