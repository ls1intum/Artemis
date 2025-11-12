import { definePreset } from '@primeuix/themes';
import Aura from '@primeuix/themes/aura';

/**
 * This overrides the PrimeNG Aura theme to use Artemis theme colors.
 * PrimeNG components reference primitive colors (green, red, yellow, orange)
 * for severities, so we override those to match Bootstrap theme colors.
 */
export const AuraArtemis = definePreset(Aura, {
    primitive: {
        // Map PrimeNG's green to Bootstrap's $success (#28a745)
        green: {
            50: '#d4edda',
            100: '#c3e6cb',
            200: '#9dd4a8',
            300: '#6cc387',
            400: '#3eb058',
            500: '#28a745', // Main success color
            600: '#218838',
            700: '#1e7e34',
            800: '#155724',
            900: '#0d3b1e',
            950: '#051f0f',
        },
        // Map PrimeNG's red to Bootstrap's $danger (#dc3545)
        red: {
            50: '#f8d7da',
            100: '#f1c2c7',
            200: '#ea9ca5',
            300: '#e47182',
            400: '#de495a',
            500: '#dc3545', // Main danger color
            600: '#c82333',
            700: '#bd2130',
            800: '#a71d2a',
            900: '#721c24',
            950: '#491217',
        },
        // Map PrimeNG's yellow/orange to Bootstrap's $warning (#ffc107)
        yellow: {
            50: '#fff9e5',
            100: '#fff3cd',
            200: '#ffe69c',
            300: '#ffda6a',
            400: '#ffcd39',
            500: '#ffc107', // Main warning color
            600: '#e0a800',
            700: '#d39e00',
            800: '#b38600',
            900: '#856404',
            950: '#533f03',
        },
        // Map orange as an alternative warning color
        orange: {
            50: '#ffe8d9',
            100: '#ffd4b8',
            200: '#ffb380',
            300: '#ff9248',
            400: '#ff7710',
            500: '#fd7e14', // Bootstrap's $orange
            600: '#e8590c',
            700: '#d14d0a',
            800: '#ba4109',
            900: '#9c3606',
            950: '#7a2a05',
        },
        // Map PrimeNG's cyan to Bootstrap's $info (#17a2b8)
        cyan: {
            50: '#d1ecf1',
            100: '#bee5eb',
            200: '#9ed5db',
            300: '#7dc5cc',
            400: '#5bb5bc',
            500: '#17a2b8', // Main info color
            600: '#138496',
            700: '#117a8b',
            800: '#10707f',
            900: '#0c5460',
            950: '#073840',
        },
    },
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
