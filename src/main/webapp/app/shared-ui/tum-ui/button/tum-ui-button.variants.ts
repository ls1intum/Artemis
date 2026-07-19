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
/** Fill style. A single input avoids the `outlined` + `text` two-boolean ambiguity (PR #13226 review). */
export type TumUiButtonVariant = 'solid' | 'outlined' | 'text';

export interface TumUiButtonVariantOptions {
    severity: TumUiButtonSeverity;
    size: TumUiButtonSize;
    variant: TumUiButtonVariant;
}

// `appearance-none` resets the native button look: Artemis imports only Tailwind utilities (no preflight),
// so a bare <button> otherwise keeps the grey UA button-face and outset border.
const BASE =
    'tum-ui-btn inline-flex appearance-none items-center justify-center gap-2 rounded-md border font-normal transition-colors focus-visible:outline-none disabled:opacity-60 disabled:pointer-events-none';

// Hover is applied by a component-level state-layer (tum-ui-button.component.scss), not per-variant
// `hover:*` utilities: those do not take effect on native <button> elements under Artemis' global styles.
const SOLID: Record<TumUiButtonSeverity, string> = {
    primary: 'bg-primary text-surface-0 border-primary',
    secondary: 'bg-surface-100 text-surface-700 border-surface-100 dark:bg-surface-800 dark:text-surface-0 dark:border-surface-800',
    success: 'bg-state-success text-surface-0 border-state-success',
    info: 'bg-state-info text-surface-0 border-state-info',
    warn: 'bg-state-warning text-surface-0 border-state-warning',
    danger: 'bg-state-danger text-surface-0 border-state-danger',
    contrast: 'bg-surface-900 text-surface-0 border-surface-900 dark:bg-surface-0 dark:text-surface-900 dark:border-surface-0',
};

const OUTLINED: Record<TumUiButtonSeverity, string> = {
    primary: 'bg-transparent text-primary border-primary',
    secondary: 'bg-transparent text-muted-color border-surface-200 dark:border-surface-700',
    success: 'bg-transparent text-state-success border-state-success',
    info: 'bg-transparent text-state-info border-state-info',
    warn: 'bg-transparent text-state-warning border-state-warning',
    danger: 'bg-transparent text-state-danger border-state-danger',
    contrast: 'bg-transparent text-surface-900 border-surface-900 dark:text-surface-0 dark:border-surface-0',
};

const TEXT: Record<TumUiButtonSeverity, string> = {
    primary: 'bg-transparent text-primary border-transparent',
    secondary: 'bg-transparent text-muted-color border-transparent',
    success: 'bg-transparent text-state-success border-transparent',
    info: 'bg-transparent text-state-info border-transparent',
    warn: 'bg-transparent text-state-warning border-transparent',
    danger: 'bg-transparent text-state-danger border-transparent',
    contrast: 'bg-transparent text-surface-900 border-transparent dark:text-surface-0',
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
