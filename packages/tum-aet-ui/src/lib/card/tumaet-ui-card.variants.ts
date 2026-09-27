/**
 * Surface treatment of a card: `elevated` lifts it off the page on the overlay background with a shadow, `muted` sets
 * it into the page on the neutral fill with a border, for grouped settings or content that should not compete with
 * the page's primary surfaces.
 */
export type TumAetUiCardVariant = 'elevated' | 'muted';

const baseClasses = 'tumaet-ui-card tumaet:flex tumaet:flex-col tumaet:rounded-xl tumaet:text-text';

const variantClasses: Record<TumAetUiCardVariant, string> = {
    elevated: 'tumaet:bg-overlay-background tumaet:shadow-sm',
    muted: 'tumaet:bg-hover-background tumaet:border tumaet:border-border',
};

/**
 * The host classes of a card with the given variant.
 *
 * @param variant the surface treatment
 */
export function tumAetUiCardClasses(variant: TumAetUiCardVariant): string {
    return `${baseClasses} ${variantClasses[variant]}`;
}
