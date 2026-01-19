import type { SidebarsConfig } from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
    default: [
        'intro',
        'setup',
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
                    items: ['guidelines/client-development', 'guidelines/client-theming', 'guidelines/client-tests'],
                },
                {
                    type: 'category',
                    label: 'Server Guidelines',
                    items: ['guidelines/server', 'guidelines/server-tests', 'guidelines/database', 'guidelines/performance', 'guidelines/criteria-builder'],
                },
                {
                    type: 'category',
                    label: 'General Guidelines',
                    items: ['guidelines/language'],
                },
            ],
        },
        'database',
        'open-source',
        'system-design',
        'test-servers',
        'e2e-testing-playwright',
        'spring-ai',
        'jenkins-localvc',
        'aeolus',
        'openapi',
        'docker-compose',
        'docker-debugging',
        'local-database-tests',
        'local-moodle-setup-for-lti',
    ],
};

export default sidebars;
