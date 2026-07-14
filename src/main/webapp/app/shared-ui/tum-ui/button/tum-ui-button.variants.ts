/**
 * Variant -> Tailwind-utility-class map for {@link TumUiButtonComponent}.
 *
 * This reimplements the spartan-ng / class-variance-authority "variants" pattern as a tiny local
 * function so the kit needs no extra npm dependency. The class strings reference only sanctioned
 * Artemis token utilities (bg-primary / text-surface-* / bg-state-* / border-*), never raw Tailwind
 * palette colors or PrimeNG --p-* primitives, so the button matches the current PrimeNG Aura look
 * and stays dark-mode-correct for free.
 */
export type TumUiButtonSeverity = 'primary' | 'secondary' | 'success' | 'info' | 'warn' | 'danger' | 'contrast';
export type TumUiButtonSize = 'small' | 'default' | 'large';

export interface TumUiButtonVariantOptions {
    severity: TumUiButtonSeverity;
    size: TumUiButtonSize;
    outlined: boolean;
    text: boolean;
}

// `appearance-none` resets the native button look: Artemis imports only Tailwind utilities (no preflight),
// so a bare <button> otherwise keeps the grey UA button-face and outset border.
const BASE =
    'tum-ui-btn inline-flex appearance-none items-center justify-center gap-2 rounded-md border font-medium transition-colors focus-visible:outline-none disabled:opacity-60 disabled:pointer-events-none';

const SOLID: Record<TumUiButtonSeverity, string> = {
    primary: 'bg-primary text-surface-0 border-primary hover:brightness-95',
    secondary: 'bg-surface-100 text-surface-700 border-surface-100 hover:bg-surface-200 dark:bg-surface-800 dark:text-surface-0 dark:border-surface-800 dark:hover:bg-surface-700',
    success: 'bg-state-success text-surface-0 border-state-success hover:brightness-95',
    info: 'bg-state-info text-surface-0 border-state-info hover:brightness-95',
    warn: 'bg-state-warning text-surface-0 border-state-warning hover:brightness-95',
    danger: 'bg-state-danger text-surface-0 border-state-danger hover:brightness-95',
    contrast: 'bg-surface-900 text-surface-0 border-surface-900 dark:bg-surface-0 dark:text-surface-900 dark:border-surface-0',
};

const OUTLINED: Record<TumUiButtonSeverity, string> = {
    primary: 'bg-transparent text-primary border-primary hover:bg-primary/10',
    secondary: 'bg-transparent text-surface-700 border-surface hover:bg-surface-100 dark:text-surface-0 dark:hover:bg-surface-800',
    success: 'bg-transparent text-state-success border-state-success hover:bg-state-success/10',
    info: 'bg-transparent text-state-info border-state-info hover:bg-state-info/10',
    warn: 'bg-transparent text-state-warning border-state-warning hover:bg-state-warning/10',
    danger: 'bg-transparent text-state-danger border-state-danger hover:bg-state-danger/10',
    contrast: 'bg-transparent text-surface-900 border-surface-900 hover:bg-surface-100 dark:text-surface-0 dark:border-surface-0 dark:hover:bg-surface-800',
};

const TEXT: Record<TumUiButtonSeverity, string> = {
    primary: 'bg-transparent text-primary border-transparent hover:bg-surface-100 dark:hover:bg-surface-800',
    secondary: 'bg-transparent text-surface-700 border-transparent hover:bg-surface-100 dark:text-surface-0 dark:hover:bg-surface-800',
    success: 'bg-transparent text-state-success border-transparent hover:bg-state-success/10',
    info: 'bg-transparent text-state-info border-transparent hover:bg-state-info/10',
    warn: 'bg-transparent text-state-warning border-transparent hover:bg-state-warning/10',
    danger: 'bg-transparent text-state-danger border-transparent hover:bg-state-danger/10',
    contrast: 'bg-transparent text-surface-900 border-transparent hover:bg-surface-100 dark:text-surface-0 dark:hover:bg-surface-800',
};

const SIZE: Record<TumUiButtonSize, string> = {
    small: 'text-sm px-2.5 py-1.5',
    default: 'text-base px-3 py-2',
    large: 'text-lg px-4 py-2.5',
};

/**
 * Compose the full class string for a button from its variant options.
 */
export function tumUiButtonClasses(options: TumUiButtonVariantOptions): string {
    const variant = options.text ? TEXT : options.outlined ? OUTLINED : SOLID;
    return `${BASE} ${variant[options.severity]} ${SIZE[options.size]}`;
}
