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
            50: '{blue.50}',
            100: '{blue.100}',
            200: '{blue.200}',
            300: '{blue.300}',
            400: '{blue.400}',
            500: '{blue.500}',
            600: '{blue.600}',
            700: '{blue.700}',
            800: '{blue.800}',
            900: '{blue.900}',
            950: '{blue.950}',
        },
    },
});
