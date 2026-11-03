import { Component, computed, input, output } from '@angular/core';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';
import { FeatureToggle } from 'app/foundation/feature-toggle/feature-toggle.service';
import { TumAetUiButtonDirective, TumAetUiButtonSeverity, TumAetUiButtonVariantOptions, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { FeatureToggleDirective } from 'app/foundation/feature-toggle/feature-toggle.directive';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

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

/** Maps the application button API to the design-system variants without rendering legacy class names. */
export function getTumAetUiButtonOptions(type: ButtonType, size: ButtonSize): TumAetUiButtonVariantOptions {
    const severities: Record<ButtonType, TumAetUiButtonSeverity> = {
        [ButtonType.DEFAULT]: 'secondary',
        [ButtonType.PRIMARY]: 'primary',
        [ButtonType.SECONDARY]: 'secondary',
        [ButtonType.SUCCESS]: 'success',
        [ButtonType.WARNING]: 'warn',
        [ButtonType.ERROR]: 'danger',
        [ButtonType.INFO]: 'info',
        [ButtonType.PRIMARY_OUTLINE]: 'primary',
        [ButtonType.SUCCESS_OUTLINE]: 'success',
        [ButtonType.ERROR_OUTLINE]: 'danger',
    };
    return {
        severity: severities[type],
        size: size === ButtonSize.SMALL ? 'small' : size === ButtonSize.LARGE ? 'large' : 'default',
        variant: [ButtonType.PRIMARY_OUTLINE, ButtonType.SUCCESS_OUTLINE, ButtonType.ERROR_OUTLINE].includes(type) ? 'outlined' : 'solid',
    };
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
    imports: [TumAetUiButtonDirective, TumAetUiTooltipDirective, FeatureToggleDirective, FaIconComponent, TranslateDirective, ArtemisTranslatePipe],
    // `title` is a translation key. A static `title="..."` in a parent template is also written to this host element,
    // where the browser would show the untranslated key as a native tooltip on hover.
    host: { '[attr.title]': 'null' },
})
export class ButtonComponent {
    protected readonly faCircleNotch = faCircleNotch;

    btnType = input(ButtonType.PRIMARY);
    btnSize = input(ButtonSize.MEDIUM);
    protected readonly buttonOptions = computed(() => getTumAetUiButtonOptions(this.btnType(), this.btnSize()));

    /** Fills the width available to the native button. */
    fullWidth = input<boolean>(false);
    // Fa-icon name.
    icon = input<IconProp | undefined>(undefined);
    // Translation placeholders, will be translated in the component.
    title = input<string>('');
    tooltip = input<string>('');
    tooltipPlacement = input<TooltipPlacement>(TooltipPlacement.TOP);

    disabled = input(false);
    isLoading = input(false);
    featureToggle = input<FeatureToggle | FeatureToggle[]>([]); // Disable by feature toggle.

    shouldSubmit = input(true);
    shouldToggle = input(false);
    toggleBreakpoint = input<'md' | 'xl'>('xl');

    onClick = output<MouseEvent>();
}
