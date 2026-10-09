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
    primary: 'tumaet:bg-primary tumaet:text-primary-contrast tumaet:border-primary',
    secondary: 'tumaet:bg-hover-background tumaet:text-text tumaet:border-hover-background',
    success: 'tumaet:bg-state-success tumaet:text-state-success-contrast tumaet:border-state-success',
    info: 'tumaet:bg-state-info tumaet:text-state-info-contrast tumaet:border-state-info',
    warn: 'tumaet:bg-state-warning tumaet:text-state-warning-contrast tumaet:border-state-warning',
    danger: 'tumaet:bg-state-danger tumaet:text-state-danger-contrast tumaet:border-state-danger',
    contrast: 'tumaet:bg-contrast-background tumaet:text-contrast tumaet:border-contrast-background',
};

const OUTLINED: Record<TumAetUiButtonSeverity, string> = {
    primary: 'tumaet:bg-transparent tumaet:text-accent tumaet:border-primary',
    secondary: 'tumaet:bg-transparent tumaet:text-text tumaet:border-border',
    success: 'tumaet:bg-transparent tumaet:text-state-success-foreground tumaet:border-state-success',
    info: 'tumaet:bg-transparent tumaet:text-state-info-foreground tumaet:border-state-info',
    warn: 'tumaet:bg-transparent tumaet:text-state-warning-foreground tumaet:border-state-warning',
    danger: 'tumaet:bg-transparent tumaet:text-state-danger-foreground tumaet:border-state-danger',
    contrast: 'tumaet:bg-transparent tumaet:text-contrast-background tumaet:border-contrast-background',
};

const TEXT: Record<TumAetUiButtonSeverity, string> = {
    primary: 'tumaet:bg-transparent tumaet:text-accent tumaet:border-transparent',
    secondary: 'tumaet:bg-transparent tumaet:text-muted tumaet:border-transparent',
    success: 'tumaet:bg-transparent tumaet:text-state-success-foreground tumaet:border-transparent',
    info: 'tumaet:bg-transparent tumaet:text-state-info-foreground tumaet:border-transparent',
    warn: 'tumaet:bg-transparent tumaet:text-state-warning-foreground tumaet:border-transparent',
    danger: 'tumaet:bg-transparent tumaet:text-state-danger-foreground tumaet:border-transparent',
    contrast: 'tumaet:bg-transparent tumaet:text-contrast-background tumaet:border-transparent',
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
