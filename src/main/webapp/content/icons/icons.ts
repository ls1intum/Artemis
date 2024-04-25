import { IconDefinition, IconName, IconPack, IconPrefix } from '@fortawesome/fontawesome-svg-core';

export const facSidebar: IconDefinition = {
    prefix: 'fac' as IconPrefix,
    iconName: 'sidebar' as IconName,
    icon: [
        24, // SVG view box width
        21, // SVG view box height
        [],
        '',
        'M2 0C0.895431 0 0 0.89543 0 2V19C0 20.1046 0.89543 21 2 21H22C23.1046 21 24 20.1046 24 19V2C24 0.895431 23.1046 0 22 0H2ZM22 1.5C22.2761 1.5 22.5 1.72386 22.5 2V19C22.5 19.2761 22.2761 19.5 22 19.5H11.25V1.5H22ZM2 1.5H9.75V19.5H2C1.72386 19.5 1.5 19.2761 1.5 19V2C1.5 1.72386 1.72386 1.5 2 1.5Z M3.25 8.25C3.25 7.83579 3.58579 7.5 4 7.5H7C7.41421 7.5 7.75 7.83579 7.75 8.25C7.75 8.66421 7.41421 9 7 9H4C3.58579 9 3.25 8.66421 3.25 8.25Z M4 10.5C3.58579 10.5 3.25 10.8358 3.25 11.25C3.25 11.6642 3.58579 12 4 12H7C7.41421 12 7.75 11.6642 7.75 11.25C7.75 10.8358 7.41421 10.5 7 10.5H4Z M4 4.5C3.58579 4.5 3.25 4.83579 3.25 5.25C3.25 5.66421 3.58579 6 4 6H7C7.41421 6 7.75 5.66421 7.75 5.25C7.75 4.83579 7.41421 4.5 7 4.5H4Z',
    ],
} as IconDefinition;

export const artemisIconPack: IconPack = {
    facSidebar,
};
