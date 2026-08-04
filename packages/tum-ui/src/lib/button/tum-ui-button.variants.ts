export type TumUiButtonSeverity = 'primary' | 'secondary' | 'success' | 'info' | 'warn' | 'danger' | 'contrast';
export type TumUiButtonSize = 'small' | 'default' | 'large';

export type TumUiButtonVariant = 'solid' | 'outlined' | 'text';

export interface TumUiButtonVariantOptions {
    severity: TumUiButtonSeverity;
    size: TumUiButtonSize;
    variant: TumUiButtonVariant;
}

const BASE =
    'tum-ui-btn tum:inline-flex tum:appearance-none tum:items-center tum:justify-center tum:gap-2 tum:rounded-md tum:border tum:font-normal tum:transition-colors tum:focus-visible:outline-none tum:disabled:opacity-60 tum:disabled:pointer-events-none';

const SOLID: Record<TumUiButtonSeverity, string> = {
    primary: 'tum:bg-primary tum:text-primary-contrast tum:border-primary',
    secondary: 'tum:bg-hover-background tum:text-text tum:border-hover-background',
    success: 'tum:bg-state-success tum:text-state-success-contrast tum:border-state-success',
    info: 'tum:bg-state-info tum:text-state-info-contrast tum:border-state-info',
    warn: 'tum:bg-state-warning tum:text-state-warning-contrast tum:border-state-warning',
    danger: 'tum:bg-state-danger tum:text-state-danger-contrast tum:border-state-danger',
    contrast: 'tum:bg-contrast-background tum:text-contrast tum:border-contrast-background',
};

const OUTLINED: Record<TumUiButtonSeverity, string> = {
    primary: 'tum:bg-transparent tum:text-accent tum:border-primary',
    secondary: 'tum:bg-transparent tum:text-text tum:border-border',
    success: 'tum:bg-transparent tum:text-state-success-foreground tum:border-state-success',
    info: 'tum:bg-transparent tum:text-state-info-foreground tum:border-state-info',
    warn: 'tum:bg-transparent tum:text-state-warning-foreground tum:border-state-warning',
    danger: 'tum:bg-transparent tum:text-state-danger-foreground tum:border-state-danger',
    contrast: 'tum:bg-transparent tum:text-contrast-background tum:border-contrast-background',
};

const TEXT: Record<TumUiButtonSeverity, string> = {
    primary: 'tum:bg-transparent tum:text-accent tum:border-transparent',
    secondary: 'tum:bg-transparent tum:text-muted tum:border-transparent',
    success: 'tum:bg-transparent tum:text-state-success-foreground tum:border-transparent',
    info: 'tum:bg-transparent tum:text-state-info-foreground tum:border-transparent',
    warn: 'tum:bg-transparent tum:text-state-warning-foreground tum:border-transparent',
    danger: 'tum:bg-transparent tum:text-state-danger-foreground tum:border-transparent',
    contrast: 'tum:bg-transparent tum:text-contrast-background tum:border-transparent',
};

const SIZE: Record<TumUiButtonSize, string> = {
    small: 'tum:text-sm tum:px-2.5 tum:py-1.5',
    default: 'tum:text-base tum:px-3 tum:py-2',
    large: 'tum:text-lg tum:px-4 tum:py-2.5',
};

const VARIANTS: Record<TumUiButtonVariant, Record<TumUiButtonSeverity, string>> = { solid: SOLID, outlined: OUTLINED, text: TEXT };

export function tumUiButtonClasses(options: TumUiButtonVariantOptions): string {
    return `${BASE} ${VARIANTS[options.variant][options.severity]} ${SIZE[options.size]}`;
}
