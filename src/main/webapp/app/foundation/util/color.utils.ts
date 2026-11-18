import { deterministicRandomValueFromString } from 'app/foundation/util/text.utils';

/**
 * Returns a background color hue for a given string.
 * @param {string | undefined} seed - The string used to determine the random value.
 */
export const getBackgroundColorHue = (seed: string | undefined): string => {
    if (seed === undefined) {
        seed = Math.random().toString();
    }
    const hue = deterministicRandomValueFromString(seed) * 360;
    return `hsl(${hue}, 50%, 50%)`; // Return an HSL color string
};

/**
 * Returns the brightness of a color. The calculation is based on https://www.w3.org/TR/AERT/#color-contrast
 * @param color - The color in hex format.
 * @returns number - The brightness of the color.
 */
export const getColorBrightness = (color: string): number => {
    // Remove the hash at the start if it's there
    color = color.replace('#', '');

    // Parse the r, g, b values
    const r = parseInt(color.substring(0, 2), 16);
    const g = parseInt(color.substring(2, 4), 16);
    const b = parseInt(color.substring(4, 6), 16);

    // Calculate the brightness
    return (r * 299 + g * 587 + b * 114) / 1000;
};

/**
 * Determines if a color is dark based on its brightness.
 * @param {string} color - The color in hex format.
 * @returns {boolean} - True if the color is dark, otherwise false.
 */
export const isColorDark = (color: string): boolean => {
    return getColorBrightness(color) < 128;
};

/**
 * Returns either black or white depending on the background color brightness.
 * @param {string} color - The background color in hex format.
 * @returns {string} - 'black' if the background color is light, 'white' if the background color is dark.
 */
export const getContrastingTextColor = (color: string): string => {
    return isColorDark(color) ? 'white' : 'black';
};

const PLAIN_HEX_COLOR = /^#[0-9a-f]{6}$/i;

/** Linearizes one sRGB channel (0-255) as defined for the WCAG relative luminance. */
const linearizeChannel = (channel: number): number => {
    const value = channel / 255;
    return value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
};

/**
 * Returns black or white, whichever has the higher WCAG contrast ratio against the given background color.
 * Only a plain `#rrggbb` color qualifies: any other value (hashless, with alpha, a named color) may not be applied as a
 * background by the browser, and a text color picked for it could end up invisible on the surface behind it.
 * @param color - The background color in `#rrggbb` format.
 * @returns 'black' or 'white', or undefined if the color is not a plain `#rrggbb` color, so the caller keeps the inherited text color.
 */
export const getReadableTextColor = (color: string | undefined): 'black' | 'white' | undefined => {
    if (!color || !PLAIN_HEX_COLOR.test(color)) {
        return undefined;
    }
    const red = linearizeChannel(parseInt(color.substring(1, 3), 16));
    const green = linearizeChannel(parseInt(color.substring(3, 5), 16));
    const blue = linearizeChannel(parseInt(color.substring(5, 7), 16));
    const luminance = 0.2126 * red + 0.7152 * green + 0.0722 * blue;
    const contrastWithBlack = (luminance + 0.05) / 0.05;
    const contrastWithWhite = 1.05 / (luminance + 0.05);
    return contrastWithBlack >= contrastWithWhite ? 'black' : 'white';
};
