import { definePreset } from '@primeuix/themes';
import Aura from '@primeuix/themes/aura';

/**
 * This overrides the default primary color of the PrimeNG Aura theme.
 * PrimeNG usually supports different shades.
 * For now all shades are here set to the same Artemis primary color.
 */
export const AuraArtemis = definePreset(Aura, {
    semantic: {
        primary: {
            50: '#3e8acc',
            100: '#3e8acc',
            200: '#3e8acc',
            300: '#3e8acc',
            400: '#3e8acc',
            500: '#3e8acc',
            600: '#3e8acc',
            700: '#3e8acc',
            800: '#3e8acc',
            900: '#3e8acc',
            950: '#3e8acc',
        },
    },
});
