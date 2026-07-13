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

const BASE =
    'tum-ui-btn inline-flex items-center justify-center gap-2 rounded-md border font-medium transition-colors focus-visible:outline-none disabled:opacity-60 disabled:pointer-events-none';

// Solid buttons fill with the WCAG-safe `-solid` tone (dark enough for white text in both themes);
// outlined/text buttons use the `-strong` foreground tone (theme-aware). See tum-ui-button.variants
// comment above and tum-ui-state-contrast.spec.ts for the verified ≥ 4.5:1 ratios.
const SOLID: Record<TumUiButtonSeverity, string> = {
    primary: 'bg-primary-solid text-surface-0 border-primary-solid hover:brightness-110',
    secondary: 'bg-surface-100 text-surface-700 border-surface-100 hover:bg-surface-200 dark:bg-surface-800 dark:text-surface-0 dark:border-surface-800 dark:hover:bg-surface-700',
    success: 'bg-state-success-solid text-surface-0 border-state-success-solid hover:brightness-110',
    info: 'bg-state-info-solid text-surface-0 border-state-info-solid hover:brightness-110',
    warn: 'bg-state-warning-solid text-surface-0 border-state-warning-solid hover:brightness-110',
    danger: 'bg-state-danger-solid text-surface-0 border-state-danger-solid hover:brightness-110',
    contrast: 'bg-surface-900 text-surface-0 border-surface-900 dark:bg-surface-0 dark:text-surface-900 dark:border-surface-0',
};

const OUTLINED: Record<TumUiButtonSeverity, string> = {
    primary: 'bg-transparent text-primary-strong border-primary hover:bg-primary/10',
    secondary: 'bg-transparent text-surface-700 border-surface hover:bg-surface-100 dark:text-surface-0 dark:hover:bg-surface-800',
    success: 'bg-transparent text-state-success-strong border-state-success hover:bg-state-success/10',
    info: 'bg-transparent text-state-info-strong border-state-info hover:bg-state-info/10',
    warn: 'bg-transparent text-state-warning-strong border-state-warning hover:bg-state-warning/10',
    danger: 'bg-transparent text-state-danger-strong border-state-danger hover:bg-state-danger/10',
    contrast: 'bg-transparent text-surface-900 border-surface-900 hover:bg-surface-100 dark:text-surface-0 dark:border-surface-0 dark:hover:bg-surface-800',
};

const TEXT: Record<TumUiButtonSeverity, string> = {
    primary: 'bg-transparent text-primary-strong border-transparent hover:bg-surface-100 dark:hover:bg-surface-800',
    secondary: 'bg-transparent text-surface-700 border-transparent hover:bg-surface-100 dark:text-surface-0 dark:hover:bg-surface-800',
    success: 'bg-transparent text-state-success-strong border-transparent hover:bg-state-success/10',
    info: 'bg-transparent text-state-info-strong border-transparent hover:bg-state-info/10',
    warn: 'bg-transparent text-state-warning-strong border-transparent hover:bg-state-warning/10',
    danger: 'bg-transparent text-state-danger-strong border-transparent hover:bg-state-danger/10',
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
