export type TumUiInputSize = 'small' | 'large';

const INPUT_BASE =
    'tum-ui-input tum:appearance-none tum:rounded-md tum:border tum:bg-tum-ui-surface-0 tum:text-tum-ui-surface-700 tum:shadow-xs tum:outline-none ' +
    'tum:transition-colors tum:duration-200 tum:placeholder:text-tum-ui-surface-500 ' +
    'tum:disabled:opacity-100 tum:disabled:bg-tum-ui-surface-200 tum:disabled:text-tum-ui-surface-500 ' +
    'tum:dark:bg-tum-ui-surface-950 tum:dark:text-tum-ui-surface-0 tum:dark:placeholder:text-tum-ui-surface-400 ' +
    'tum:dark:disabled:bg-tum-ui-surface-700 tum:dark:disabled:text-tum-ui-surface-400';

const INPUT_BORDER =
    'tum:border-tum-ui-surface-300 tum:enabled:hover:border-tum-ui-surface-400 tum:enabled:focus:border-tum-ui-primary tum:dark:border-tum-ui-surface-600 tum:dark:enabled:hover:border-tum-ui-surface-500';

const INPUT_BORDER_INVALID = 'tum:border-tum-ui-state-danger';

const INPUT_SIZE: Record<TumUiInputSize, string> = {
    small: 'tum:text-sm tum:px-2.5 tum:py-1.5',
    large: 'tum:text-lg tum:px-3.5 tum:py-2.5',
};
const INPUT_SIZE_NORMAL = 'tum:text-base tum:px-3 tum:py-2';

export interface TumUiInputClassOptions {
    size?: TumUiInputSize;
    invalid: boolean;
}

export function tumUiInputClasses(options: TumUiInputClassOptions): string {
    const size = options.size ? INPUT_SIZE[options.size] : INPUT_SIZE_NORMAL;
    const border = options.invalid ? INPUT_BORDER_INVALID : INPUT_BORDER;
    return `${INPUT_BASE} ${size} ${border}`;
}
