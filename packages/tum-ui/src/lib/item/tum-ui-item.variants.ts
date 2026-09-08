import { TumUiSize } from '../foundation/tum-ui-vocabulary';

/** Surface treatment of a row. */
export type TumUiItemVariant = 'default' | 'outline' | 'muted';

const ITEM_BASE = 'tum-ui-item tum:flex tum:min-w-0 tum:w-full tum:items-center tum:gap-3 tum:text-text tum:no-underline';

const ITEM_VARIANT: Record<TumUiItemVariant, string> = {
    // A row inside a grouped list: the group draws the rules, so the row draws nothing.
    default: 'tum:rounded-md',
    // A row that stands on its own, away from a group.
    outline: 'tum:rounded-md tum:border tum:border-border tum:bg-content-background',
    // A row that is a container for something else, and should recede behind it.
    muted: 'tum:rounded-md tum:bg-hover-background',
};

const ITEM_SIZE: Record<TumUiSize, string> = {
    small: 'tum:px-2 tum:py-1.5 tum:text-sm',
    medium: 'tum:px-3 tum:py-2 tum:text-sm',
    large: 'tum:px-4 tum:py-3 tum:text-base',
};

// Only a row that is genuinely a link or a button gets hover and focus affordances. A hover highlight on a row
// that does nothing is a promise the row cannot keep.
const ITEM_INTERACTIVE =
    'tum:cursor-pointer tum:transition-colors tum:hover:bg-hover-background ' +
    'tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus tum:focus-visible:outline-offset-2';

export interface TumUiItemClassOptions {
    variant: TumUiItemVariant;
    size: TumUiSize;
    interactive: boolean;
}

/**
 * The one class table behind both entry points, so `tum-ui-item` and `[tumUiItem]` cannot drift apart.
 */
export function tumUiItemClasses(options: TumUiItemClassOptions): string {
    const interactive = options.interactive ? ` ${ITEM_INTERACTIVE}` : '';
    return `${ITEM_BASE} ${ITEM_VARIANT[options.variant]} ${ITEM_SIZE[options.size]}${interactive}`;
}
