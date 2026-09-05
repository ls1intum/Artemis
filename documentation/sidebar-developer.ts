import type { SidebarsConfig } from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
    default: [
        'intro',
        'setup',
        'development-process',
        'work-with-ai',
        'reviewer-guidelines',
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
                        'guidelines/client-development',
                        'guidelines/client-theming',
                        'guidelines/client-tests',
                        'guidelines/tum-ui-kit',
                        {
                            type: 'doc',
                            label: 'TUM UI component reference',
                            id: 'tum-ui',
                        },
                    ],
                },
                {
                    type: 'category',
                    label: 'Server Guidelines',
                    items: [
                        'guidelines/server-development',
                        'guidelines/rest-api',
                        'guidelines/server-tests',
                        'guidelines/database',
                        'guidelines/database-migration-consolidation',
                        'guidelines/caching',
                        'guidelines/distributed-data',
                        'guidelines/performance',
                        'guidelines/criteria-builder',
                    ],
                },
                {
                    type: 'category',
                    label: 'General Guidelines',
                    items: ['guidelines/language'],
                },
            ],
        },
        'local-user-management',
        'open-source',
        'system-design',
        'feature-usage',
        'feature-usage-catalogue',
        'test-servers',
        'builds-and-dependencies',
        'e2e-testing-playwright',
        'e2e-testing-iris',
        'nightly-ci',
        'spring-ai',
        'ai-pipelines',
        'deimos',
        'openapi',
        'docker-compose',
        'mailpit-setup',
        'keycloak-saml2-setup',
        'keycloak-oidc-setup',
        'docker-debugging',
        'local-database-tests',
        'local-moodle-setup-for-lti',
        'weaviate-setup',
        {
            type: 'category',
            label: 'Hyperion',
            items: ['hyperion/consistency-check', 'hyperion/quiz-generation'],
        },
    ],
};

export default sidebars;
