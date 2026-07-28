/**
 * Variant -> Tailwind-utility-class map for {@link TumUiButtonComponent}.
 *
 * This reimplements the spartan-ng / class-variance-authority "variants" pattern as a tiny local
 * function so the kit needs no extra npm dependency. The class strings reference only sanctioned
 * Artemis token utilities (bg-tum-ui-primary / text-tum-ui-surface-* / bg-state-* / border-*), never raw Tailwind
 * palette colors or PrimeNG --p-* primitives, so the button matches the current PrimeNG Aura look
 * and stays dark-mode-correct for free.
 */
export type TumUiButtonSeverity = 'primary' | 'secondary' | 'success' | 'info' | 'warn' | 'danger' | 'contrast';
export type TumUiButtonSize = 'small' | 'default' | 'large';
/** Fill style. A single input avoids the `outlined` + `text` two-boolean ambiguity (PR #13226 review). */
export type TumUiButtonVariant = 'solid' | 'outlined' | 'text';

export interface TumUiButtonVariantOptions {
    severity: TumUiButtonSeverity;
    size: TumUiButtonSize;
    variant: TumUiButtonVariant;
}

const BASE =
    'tum-ui-btn inline-flex appearance-none items-center justify-center gap-2 rounded-md border font-normal transition-colors focus-visible:outline-none disabled:opacity-60 disabled:pointer-events-none';

const SOLID: Record<TumUiButtonSeverity, string> = {
    primary: 'bg-tum-ui-primary text-tum-ui-surface-0 border-tum-ui-primary',
    secondary: 'bg-tum-ui-surface-100 text-tum-ui-surface-700 border-tum-ui-surface-100 dark:bg-tum-ui-surface-800 dark:text-tum-ui-surface-0 dark:border-tum-ui-surface-800',
    success: 'bg-tum-ui-state-success text-tum-ui-surface-0 border-tum-ui-state-success',
    info: 'bg-tum-ui-state-info text-tum-ui-surface-0 border-tum-ui-state-info',
    warn: 'bg-tum-ui-state-warning text-tum-ui-surface-0 border-tum-ui-state-warning',
    danger: 'bg-tum-ui-state-danger text-tum-ui-surface-0 border-tum-ui-state-danger',
    contrast: 'bg-tum-ui-surface-900 text-tum-ui-surface-0 border-tum-ui-surface-900 dark:bg-tum-ui-surface-0 dark:text-tum-ui-surface-900 dark:border-tum-ui-surface-0',
};

const OUTLINED: Record<TumUiButtonSeverity, string> = {
    primary: 'bg-transparent text-tum-ui-primary border-tum-ui-primary',
    secondary: 'bg-transparent text-tum-ui-muted border-tum-ui-surface-200 dark:border-tum-ui-surface-700',
    success: 'bg-transparent text-tum-ui-state-success border-tum-ui-state-success',
    info: 'bg-transparent text-tum-ui-state-info border-tum-ui-state-info',
    warn: 'bg-transparent text-tum-ui-state-warning border-tum-ui-state-warning',
    danger: 'bg-transparent text-tum-ui-state-danger border-tum-ui-state-danger',
    contrast: 'bg-transparent text-tum-ui-surface-900 border-tum-ui-surface-900 dark:text-tum-ui-surface-0 dark:border-tum-ui-surface-0',
};

const TEXT: Record<TumUiButtonSeverity, string> = {
    primary: 'bg-transparent text-tum-ui-primary border-transparent',
    secondary: 'bg-transparent text-tum-ui-muted border-transparent',
    success: 'bg-transparent text-tum-ui-state-success border-transparent',
    info: 'bg-transparent text-tum-ui-state-info border-transparent',
    warn: 'bg-transparent text-tum-ui-state-warning border-transparent',
    danger: 'bg-transparent text-tum-ui-state-danger border-transparent',
    contrast: 'bg-transparent text-tum-ui-surface-900 border-transparent dark:text-tum-ui-surface-0',
};

const SIZE: Record<TumUiButtonSize, string> = {
    small: 'text-sm px-2.5 py-1.5',
    default: 'text-base px-3 py-2',
    large: 'text-lg px-4 py-2.5',
};

const VARIANTS: Record<TumUiButtonVariant, Record<TumUiButtonSeverity, string>> = { solid: SOLID, outlined: OUTLINED, text: TEXT };

/**
 * Compose the full class string for a button from its variant options.
 */
export function tumUiButtonClasses(options: TumUiButtonVariantOptions): string {
    return `${BASE} ${VARIANTS[options.variant][options.severity]} ${SIZE[options.size]}`;
}
