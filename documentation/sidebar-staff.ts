import type { SidebarsConfig } from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
    default: [
        'intro',
        'setup',
        'spring-ai',
        {
            type: 'category',
            label: 'Coding and Design Guidelines',
            link: {
                type: 'doc',
                id: 'guidelines/index',
            },
            items: [
                {
                    type: 'category',
                    label: 'Client Guidelines',
                    items: [
                        'guidelines/client',
                        'guidelines/client-design',
                        'guidelines/client-tests',
                    ],
                },
                {
                    type: 'category',
                    label: 'Server Guidelines',
                    items: [
                        'guidelines/server',
                        'guidelines/server-tests',
                        'guidelines/database',
                        'guidelines/performance',
                        'guidelines/criteria-builder',
                    ],
                },
                {
                    type: 'category',
                    label: 'General Guidelines',
                    items: [
                        'guidelines/language',
                    ],
                },
            ],
        },
    ],
};

export default sidebars;
