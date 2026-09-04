export type TumUiInputSize = 'small' | 'large';

const INPUT_BASE =
    'tum-ui-input tum:box-border tum:appearance-none tum:rounded-md tum:border tum:bg-control-background tum:text-text tum:shadow-xs ' +
    'tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus tum:focus-visible:outline-offset-2 ' +
    'tum:transition-colors tum:duration-[var(--tumaet-ui-duration-state)] tum:placeholder:text-muted ' +
    'tum:disabled:opacity-100 tum:disabled:bg-disabled-background tum:disabled:text-disabled';

const INPUT_BORDER = 'tum:border-control-border tum:enabled:hover:border-control-border-hover tum:enabled:focus:border-focus';

const INPUT_BORDER_INVALID = 'tum:border-state-danger';

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
