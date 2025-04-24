import { deterministicRandomValueFromString } from 'app/shared/util/text.utils';

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
