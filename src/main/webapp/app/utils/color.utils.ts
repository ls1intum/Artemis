import { deterministicRandomValueFromString } from 'app/utils/text.utils';

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
