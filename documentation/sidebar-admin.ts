import type { SidebarsConfig } from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
    default: [
        'intro',
        'access-rights',
        'artemis-intelligence',
        {
            type: 'category',
            label: 'Production Setup',
            link: {
                type: 'doc',
                id: 'production-setup/index'
            },
            items: [
                'production-setup/security',
                'production-setup/customization',
                'production-setup/legal-documents',
                'production-setup/additional-tips',
                'production-setup/programming-exercise-adjustments',
                'production-setup/multiple-artemis-instances',
            ]
        },
        'hyperion',
        'adaptive-learning',
    ],
};

export default sidebars;
