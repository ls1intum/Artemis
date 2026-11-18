export type TumAetUiButtonSeverity = 'primary' | 'secondary' | 'success' | 'info' | 'warn' | 'danger' | 'contrast';
export type TumAetUiButtonSize = 'small' | 'default' | 'large';

export type TumAetUiButtonVariant = 'solid' | 'outlined' | 'text';

export interface TumAetUiButtonVariantOptions {
    severity: TumAetUiButtonSeverity;
    size: TumAetUiButtonSize;
    variant: TumAetUiButtonVariant;
}

const BASE =
    'tumaet-ui-btn tumaet:inline-flex tumaet:appearance-none tumaet:items-center tumaet:justify-center tumaet:gap-2 tumaet:rounded-md tumaet:border tumaet:font-normal tumaet:transition-colors tumaet:focus-visible:outline-none tumaet:disabled:opacity-60 tumaet:disabled:pointer-events-none';

const SOLID: Record<TumAetUiButtonSeverity, string> = {
    primary: 'tumaet:bg-button-primary tumaet:text-button-primary-contrast tumaet:border-button-primary',
    secondary: 'tumaet:bg-button-secondary tumaet:text-button-secondary-contrast tumaet:border-button-secondary',
    success: 'tumaet:bg-button-success tumaet:text-button-success-contrast tumaet:border-button-success',
    info: 'tumaet:bg-button-info tumaet:text-button-info-contrast tumaet:border-button-info',
    warn: 'tumaet:bg-button-warn tumaet:text-button-warn-contrast tumaet:border-button-warn',
    danger: 'tumaet:bg-button-danger tumaet:text-button-danger-contrast tumaet:border-button-danger',
    contrast: 'tumaet:bg-button-contrast tumaet:text-button-contrast-contrast tumaet:border-button-contrast',
};

const OUTLINED: Record<TumAetUiButtonSeverity, string> = {
    primary: 'tumaet:bg-transparent tumaet:text-button-primary-foreground tumaet:border-button-primary',
    secondary: 'tumaet:bg-transparent tumaet:text-button-secondary-foreground tumaet:border-button-secondary-border',
    success: 'tumaet:bg-transparent tumaet:text-button-success-foreground tumaet:border-button-success',
    info: 'tumaet:bg-transparent tumaet:text-button-info-foreground tumaet:border-button-info',
    warn: 'tumaet:bg-transparent tumaet:text-button-warn-foreground tumaet:border-button-warn',
    danger: 'tumaet:bg-transparent tumaet:text-button-danger-foreground tumaet:border-button-danger',
    contrast: 'tumaet:bg-transparent tumaet:text-button-contrast-foreground tumaet:border-button-contrast',
};

const TEXT: Record<TumAetUiButtonSeverity, string> = {
    primary: 'tumaet:bg-transparent tumaet:text-button-primary-foreground tumaet:border-transparent',
    secondary: 'tumaet:bg-transparent tumaet:text-button-secondary-foreground tumaet:border-transparent',
    success: 'tumaet:bg-transparent tumaet:text-button-success-foreground tumaet:border-transparent',
    info: 'tumaet:bg-transparent tumaet:text-button-info-foreground tumaet:border-transparent',
    warn: 'tumaet:bg-transparent tumaet:text-button-warn-foreground tumaet:border-transparent',
    danger: 'tumaet:bg-transparent tumaet:text-button-danger-foreground tumaet:border-transparent',
    contrast: 'tumaet:bg-transparent tumaet:text-button-contrast-foreground tumaet:border-transparent',
};

const SIZE: Record<TumAetUiButtonSize, string> = {
    small: 'tumaet:text-sm tumaet:px-2.5 tumaet:py-1.5',
    default: 'tumaet:text-base tumaet:px-3 tumaet:py-2',
    large: 'tumaet:text-lg tumaet:px-4 tumaet:py-2.5',
};

const VARIANTS: Record<TumAetUiButtonVariant, Record<TumAetUiButtonSeverity, string>> = { solid: SOLID, outlined: OUTLINED, text: TEXT };

export function tumAetUiButtonClasses(options: TumAetUiButtonVariantOptions): string {
    return `${BASE} ${VARIANTS[options.variant][options.severity]} ${SIZE[options.size]}`;
}
