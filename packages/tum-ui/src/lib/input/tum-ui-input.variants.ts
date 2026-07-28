export type TumUiInputSize = 'small' | 'large';

const INPUT_BASE =
    'tum-ui-input tum:appearance-none tum:rounded-md tum:border tum:bg-tum-ui-control-background tum:text-tum-ui-text tum:shadow-xs tum:outline-none ' +
    'tum:transition-colors tum:duration-200 tum:placeholder:text-tum-ui-muted ' +
    'tum:disabled:opacity-100 tum:disabled:bg-tum-ui-disabled-background tum:disabled:text-tum-ui-disabled';

const INPUT_BORDER = 'tum:border-tum-ui-control-border tum:enabled:hover:border-tum-ui-control-border-hover tum:enabled:focus:border-tum-ui-primary';

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
