export type TumAetUiInputSize = 'small' | 'large';

const INPUT_BASE =
    'tumaet-ui-input tumaet:box-border tumaet:appearance-none tumaet:rounded-md tumaet:border tumaet:bg-control-background tumaet:text-text tumaet:shadow-xs ' +
    'tumaet:focus-visible:outline tumaet:focus-visible:outline-2 tumaet:focus-visible:outline-focus tumaet:focus-visible:outline-offset-2 ' +
    'tumaet:transition-colors tumaet:duration-200 tumaet:placeholder:text-muted ' +
    'tumaet:disabled:opacity-100 tumaet:disabled:bg-disabled-background tumaet:disabled:text-disabled';

const INPUT_BORDER = 'tumaet:border-control-border tumaet:enabled:hover:border-control-border-hover tumaet:enabled:focus:border-focus';

const INPUT_BORDER_INVALID = 'tumaet:border-state-danger';

const INPUT_SIZE: Record<TumAetUiInputSize, string> = {
    small: 'tumaet:text-sm tumaet:px-2.5 tumaet:py-1.5',
    large: 'tumaet:text-lg tumaet:px-3.5 tumaet:py-2.5',
};
const INPUT_SIZE_NORMAL = 'tumaet:text-base tumaet:px-3 tumaet:py-2';

export interface TumAetUiInputClassOptions {
    size?: TumAetUiInputSize;
    invalid: boolean;
}

export function tumAetUiInputClasses(options: TumAetUiInputClassOptions): string {
    const size = options.size ? INPUT_SIZE[options.size] : INPUT_SIZE_NORMAL;
    const border = options.invalid ? INPUT_BORDER_INVALID : INPUT_BORDER;
    return `${INPUT_BASE} ${size} ${border}`;
}
