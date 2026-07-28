export type TumUiInputSize = 'small' | 'large';

const INPUT_BASE =
    'tum-ui-input appearance-none rounded-md border bg-tum-ui-surface-0 text-tum-ui-surface-700 shadow-xs outline-none ' +
    'transition-colors duration-200 placeholder:text-tum-ui-surface-500 ' +
    'disabled:opacity-100 disabled:bg-tum-ui-surface-200 disabled:text-tum-ui-surface-500 ' +
    'dark:bg-tum-ui-surface-950 dark:text-tum-ui-surface-0 dark:placeholder:text-tum-ui-surface-400 ' +
    'dark:disabled:bg-tum-ui-surface-700 dark:disabled:text-tum-ui-surface-400';

const INPUT_BORDER =
    'border-tum-ui-surface-300 enabled:hover:border-tum-ui-surface-400 enabled:focus:border-tum-ui-primary dark:border-tum-ui-surface-600 dark:enabled:hover:border-tum-ui-surface-500';

const INPUT_BORDER_INVALID = 'border-tum-ui-state-danger';

const INPUT_SIZE: Record<TumUiInputSize, string> = {
    small: 'text-sm px-2.5 py-1.5',
    large: 'text-lg px-3.5 py-2.5',
};
const INPUT_SIZE_NORMAL = 'text-base px-3 py-2';

export interface TumUiInputClassOptions {
    size?: TumUiInputSize;
    invalid: boolean;
}

export function tumUiInputClasses(options: TumUiInputClassOptions): string {
    const size = options.size ? INPUT_SIZE[options.size] : INPUT_SIZE_NORMAL;
    const border = options.invalid ? INPUT_BORDER_INVALID : INPUT_BORDER;
    return `${INPUT_BASE} ${size} ${border}`;
}
