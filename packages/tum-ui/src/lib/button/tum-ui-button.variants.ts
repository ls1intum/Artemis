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
    primary: 'tum:bg-tum-ui-primary tum:text-tum-ui-primary-contrast tum:border-tum-ui-primary',
    secondary:
        'tum:bg-tum-ui-surface-100 tum:text-tum-ui-surface-700 tum:border-tum-ui-surface-100 tum:dark:bg-tum-ui-surface-800 tum:dark:text-tum-ui-surface-0 tum:dark:border-tum-ui-surface-800',
    success: 'tum:bg-tum-ui-state-success tum:text-tum-ui-surface-0 tum:border-tum-ui-state-success',
    info: 'tum:bg-tum-ui-state-info tum:text-tum-ui-surface-0 tum:border-tum-ui-state-info',
    warn: 'tum:bg-tum-ui-state-warning tum:text-tum-ui-surface-0 tum:border-tum-ui-state-warning',
    danger: 'tum:bg-tum-ui-state-danger tum:text-tum-ui-surface-0 tum:border-tum-ui-state-danger',
    contrast:
        'tum:bg-tum-ui-surface-900 tum:text-tum-ui-surface-0 tum:border-tum-ui-surface-900 tum:dark:bg-tum-ui-surface-0 tum:dark:text-tum-ui-surface-900 tum:dark:border-tum-ui-surface-0',
};

const OUTLINED: Record<TumUiButtonSeverity, string> = {
    primary: 'tum:bg-transparent tum:text-tum-ui-primary tum:border-tum-ui-primary',
    secondary: 'tum:bg-transparent tum:text-tum-ui-muted tum:border-tum-ui-surface-200 tum:dark:border-tum-ui-surface-700',
    success: 'tum:bg-transparent tum:text-tum-ui-state-success tum:border-tum-ui-state-success',
    info: 'tum:bg-transparent tum:text-tum-ui-state-info tum:border-tum-ui-state-info',
    warn: 'tum:bg-transparent tum:text-tum-ui-state-warning tum:border-tum-ui-state-warning',
    danger: 'tum:bg-transparent tum:text-tum-ui-state-danger tum:border-tum-ui-state-danger',
    contrast: 'tum:bg-transparent tum:text-tum-ui-surface-900 tum:border-tum-ui-surface-900 tum:dark:text-tum-ui-surface-0 tum:dark:border-tum-ui-surface-0',
};

const TEXT: Record<TumUiButtonSeverity, string> = {
    primary: 'tum:bg-transparent tum:text-tum-ui-primary tum:border-transparent',
    secondary: 'tum:bg-transparent tum:text-tum-ui-muted tum:border-transparent',
    success: 'tum:bg-transparent tum:text-tum-ui-state-success tum:border-transparent',
    info: 'tum:bg-transparent tum:text-tum-ui-state-info tum:border-transparent',
    warn: 'tum:bg-transparent tum:text-tum-ui-state-warning tum:border-transparent',
    danger: 'tum:bg-transparent tum:text-tum-ui-state-danger tum:border-transparent',
    contrast: 'tum:bg-transparent tum:text-tum-ui-surface-900 tum:border-transparent tum:dark:text-tum-ui-surface-0',
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
