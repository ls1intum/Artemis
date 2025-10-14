//@ts-ignore you will need to install this dependency
import { definePreset } from '@primeng/themes';
//@ts-ignore you will need to install this dependency
import Aura from '@primeng/themes/aura';

export default {
  theme: {
    preset: definePreset(Aura, {
      semantic: {
        primary: {
          50: '#eaf3fd',
          100: '#c0daf8',
          200: '#97c1f4',
          300: '#6da8ef',
          400: '#438fea',
          500: '#176bcf',
          600: '#1561bc',
          700: '#104b92',
          800: '#0b3668',
          900: '#072140',
          950: '#030c17',
        },
        colorScheme: {
          light: {
            surface: {
              0: '#ffffff',
              50: '{neutral.50}',
              100: '{neutral.100}',
              200: '{neutral.200}',
              300: '{neutral.300}',
              400: '{neutral.400}',
              500: '{neutral.500}',
              600: '{neutral.600}',
              700: '{neutral.700}',
              800: '{neutral.800}',
              900: '{neutral.900}',
              950: '{neutral.950}',
              text: '{neutral.600}',
            },
            primary: {
              color: '{primary.900}',
              contrastColor: '#ffffff',
              hoverColor: '{primary.800}',
              activeColor: '{primary.800}',
            },
            text: {
              mutedColor: '{neutral.600}',
              hoverMutedColor: '{surface.500}',
            },
            highlight: {
              background: '{primary.800}',
              focusBackground: '{primary.700}',
              color: '#ffffff',
              focusColor: '#ffffff',
            },
          },
          dark: {
            surface: {
              0: '#ffffff',
              50: '{neutral.50}',
              100: '{neutral.100}',
              200: '{neutral.200}',
              300: '{neutral.300}',
              400: '{neutral.400}',
              500: '{neutral.500}',
              600: '{neutral.600}',
              700: '{neutral.700}',
              800: '{neutral.800}',
              900: '{neutral.900}',
              950: '{neutral.950}',
            },
            primary: {
              color: '{primary.800}',
              contrastColor: '{primary.50}',
              hoverColor: '{primary.700}',
              activeColor: '{primary.400}',
            },
            text: {
              mutedColor: '{neutral.400}',
              hoverMutedColor: '{surface.300}',
            },
            highlight: {
              background: '{primary.800}',
              focusBackground: '{primary.700}',
              color: '#ffffff',
              focusColor: '#ffffff',
            },
          },
        },
      },

      components: {
        toggleswitch: {
          colorScheme: {
            checkedBackground: '{emerald.500}',
            checkedHoverBackground: '{emerald.500}',
          },
        },
        button: {
          colorScheme: {
            dark: {
              text: {
                primary: {
                  hoverBackground: '{surface.700}',
                  color: '{primary.activeColor}',
                },
              },
            },
          },
        },
        tabs: {
          colorScheme: {
            dark: {
              tab: {
                activeColor: '{primary.activeColor}',
              },
              activeBar: {
                background: '{primary.activeColor}',
              },
            },
          },
        },
      },
    }),
    options: {
      darkModeSelector: '.dark-mode-enabled',
      cssLayer: {
        name: 'primeng',
        order: 'tailwind-base, primeng, tailwind-utilities',
      },
    },
  },
};
