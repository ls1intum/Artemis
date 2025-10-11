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
            50: '#d8e8f5',
            100: '#c5dcf0',
            200: '#9fc5e6',
            300: '#65a1d6',
            400: '#3e8acc',
            500: '#387cb8',
            600: '#326ea3',
            700: '#2b618f',
            800: '#1f4566',
            900: '#13293d',
            950: '#060e14',
        },
    },
});
