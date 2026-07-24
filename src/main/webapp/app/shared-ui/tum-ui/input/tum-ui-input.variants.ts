/**
 * Variant -> Tailwind-utility-class map for {@link TumUiInputDirective}.
 *
 * Reproduces PrimeNG's `.p-inputtext` / `.p-textarea` (Aura) look using only sanctioned Artemis token
 * utilities (bg-surface-* / text-surface-* / border-surface-* / border-primary / border-state-danger),
 * never raw Tailwind palette colors or PrimeNG --p-* primitives, so a migrated `<input tumUiInput>` matches
 * the current PrimeNG Aura input and stays dark-mode-correct for free (via the `dark:` variant, which
 * tailwind.css binds to the same `prime-ng-use-dark-theme` selector PrimeNG uses).
 *
 * Exact Aura sources reproduced:
 * - metrics: font-size 1rem, padding 0.5rem/0.75rem, radius {border.radius.md}=6px, 1px border, subtle shadow
 *   (`@primeuix/styles` inputtext base + `formField` sm/lg tokens).
 * - light colors: background {surface.0}, color {surface.700}, border {surface.300}, hover {surface.400},
 *   focus {primary.color}, disabled bg {surface.200}/color {surface.500}, placeholder {surface.500}.
 * - dark colors: background {surface.950}, color {surface.0}, border {surface.600}, hover {surface.500},
 *   disabled bg {surface.700}/color {surface.400}, placeholder {surface.400}.
 */
export type TumUiInputSize = 'small' | 'large';

// Base styling shared by <input> and <textarea>. Border COLOR lives in the border maps below (not here) so
// the invalid state can win deterministically: Tailwind emits `:hover`/`:focus` variant utilities after plain
// ones, so a plain `border-state-danger` would otherwise lose to `enabled:hover:border-surface-400` — PrimeNG's
// own `.p-invalid` vs `:enabled:hover` specificity quirk. Splitting the branches makes invalid a fixed color.
const INPUT_BASE =
    'tum-ui-input appearance-none rounded-md border bg-surface-0 text-surface-700 shadow-xs outline-none ' +
    'transition-colors duration-200 placeholder:text-surface-500 ' +
    'disabled:opacity-100 disabled:bg-surface-200 disabled:text-surface-500 ' +
    'dark:bg-surface-950 dark:text-surface-0 dark:placeholder:text-surface-400 ' +
    'dark:disabled:bg-surface-700 dark:disabled:text-surface-400';

// Valid border: rest {surface.300/600}, hover {surface.400/500} (enabled only, matching `:enabled:hover`),
// focus {primary.color}. PrimeNG's `formField.focusRing` is width 0 / none, so focus only recolors the border
// (no visible ring) — reproduced here as a border-color change with the outline suppressed.
const INPUT_BORDER = 'border-surface-300 enabled:hover:border-surface-400 enabled:focus:border-primary dark:border-surface-600 dark:enabled:hover:border-surface-500';

// Invalid border: PrimeNG `.p-invalid` keeps a fixed invalid border regardless of hover/focus. Uses the
// semantic danger token (see deviation note in the directive: slightly more saturated than Aura's lightened
// red.400/red.300 border, chosen to keep the color semantic like tum-ui-tag / -message / -button).
const INPUT_BORDER_INVALID = 'border-state-danger';

const INPUT_SIZE: Record<TumUiInputSize, string> = {
    // Aura formField.sm: font 0.875rem, padding 0.375rem/0.625rem.
    small: 'text-sm px-2.5 py-1.5',
    // Aura formField.lg: font 1.125rem, padding 0.625rem/0.875rem.
    large: 'text-lg px-3.5 py-2.5',
};
// Aura formField default: font 1rem, padding 0.5rem/0.75rem.
const INPUT_SIZE_NORMAL = 'text-base px-3 py-2';

export interface TumUiInputClassOptions {
    size?: TumUiInputSize;
    invalid: boolean;
}

/**
 * Compose the full class string applied to a `<input tumUiInput>` / `<textarea tumUiInput>` element.
 */
export function tumUiInputClasses(options: TumUiInputClassOptions): string {
    const size = options.size ? INPUT_SIZE[options.size] : INPUT_SIZE_NORMAL;
    const border = options.invalid ? INPUT_BORDER_INVALID : INPUT_BORDER;
    return `${INPUT_BASE} ${size} ${border}`;
}
