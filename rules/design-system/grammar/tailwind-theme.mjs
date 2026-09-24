import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import postcss from 'postcss';
import colors from 'tailwindcss/colors';

// Read framework defaults from the installed version, not a generated copy of Tailwind's theme.
const require = createRequire(import.meta.url);
const theme = postcss.parse(readFileSync(require.resolve('tailwindcss/theme.css'), 'utf8'));
function scale(prefix) {
    const values = {};
    theme.walkDecls((declaration) => {
        if (declaration.prop.startsWith(prefix)) {
            const name = declaration.prop.slice(prefix.length);
            if (!name.includes('--')) values[name] = declaration.value;
        }
    });
    return values;
}
export const FONT_SIZES = scale('--text-');
export const RADII = scale('--radius-');
export const PALETTE = Object.fromEntries(
    Object.entries(colors).flatMap(([name, value]) => (typeof value === 'string' ? [[name, value]] : Object.entries(value).map(([shade, color]) => [`${name}-${shade}`, color]))),
);
