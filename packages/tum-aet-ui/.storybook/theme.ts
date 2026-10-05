export type ThemeName = 'light' | 'dark';

export function preferredTheme(): ThemeName {
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

export function resolveTheme(value: unknown): ThemeName {
    return value === 'light' || value === 'dark' ? value : preferredTheme();
}
