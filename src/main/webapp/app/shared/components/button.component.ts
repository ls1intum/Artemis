import { Component, EventEmitter, Input, Output } from '@angular/core';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';

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

/**
 * A generic button component that has a disabled & loading state.
 * The only event output is the click.
 *
 * Can be used as a button with text and/or icon.
 */
@Component({
    selector: 'jhi-button',
    templateUrl: './button.component.html',
})
export class ButtonComponent {
    @Input() btnType = ButtonType.PRIMARY;
    @Input() btnSize = ButtonSize.MEDIUM;
    // Fa-icon name.
    @Input() icon: IconProp;
    // Translation placeholders, will be translated in the component.
    @Input() title: string;
    @Input() tooltip: string;

    @Input() disabled = false;
    @Input() isLoading = false;
    @Input() featureToggle: FeatureToggle; // Disable by feature toggle.

    @Input() shouldSubmit = true;

    @Output() onClick = new EventEmitter<MouseEvent>();

    // Icons
    faCircleNotch = faCircleNotch;
}
