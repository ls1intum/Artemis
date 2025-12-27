import type { SidebarsConfig } from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
    default: [
        'intro',
        'setup',
        'spring-ai',
        {
            type: 'category',
            label: 'Contributor Guide',
            items: [
                'development-process',
                'reviewer-guidelines',
                'local-user-management',
                'builds-and-dependencies',
            ],
        },
    ],
};

export default sidebars;
