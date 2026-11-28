import { readFileSync } from 'node:fs';

const THEME_DIRECTORY = 'src/main/webapp/content/css/themes';

function declarations(file: string): Map<string, string> {
    const css = readFileSync(`${THEME_DIRECTORY}/${file}`, 'utf8').replace(/\/\*[\s\S]*?\*\//g, '');
    return new Map([...css.matchAll(/--([\w-]+):\s*([^;]+);/g)].map(([, name, value]) => [name, value.trim().replace(/\s+/g, ' ')]));
}

/**
 * The tokens of a theme as the browser resolves them on the root element: the values of `themes/light.css`, for the dark
 * theme overridden by those of `themes/dark.css`, with every `var()` reference to another token replaced by its value.
 * The values are read from the stylesheets, so a spec that uses this sees a change to a token.
 */
export function readThemeTokens(theme: 'light' | 'dark'): Map<string, string> {
    const declared = declarations('light.css');
    if (theme === 'dark') {
        declarations('dark.css').forEach((value, name) => declared.set(name, value));
    }
    const resolve = (value: string, depth = 0): string => {
        if (depth > 20) {
            throw new Error(`The theme tokens reference each other in a cycle: ${value}`);
        }
        return value.replace(/var\(--([\w-]+)\)/g, (reference, name) => (declared.has(name) ? resolve(declared.get(name)!, depth + 1) : reference));
    };
    return new Map([...declared].map(([name, value]) => [name, resolve(value)]));
}
