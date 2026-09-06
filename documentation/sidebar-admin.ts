import type { SidebarsConfig } from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
    default: [
        'intro',
        'courses',
        'course-management',
        'access-rights',
        'artemis-intelligence',
        'scaling',
        {
            type: 'category',
            label: 'Production Setup',
            link: {
                type: 'doc',
                id: 'production-setup/index',
            },
            items: [
                'production-setup/security',
                'production-setup/integrated-code-lifecycle-setup',
                'production-setup/build-runners',
                'production-setup/customization',
                'production-setup/legal-documents',
                'production-setup/additional-tips',
                'production-setup/programming-exercise-adjustments',
                'production-setup/multiple-artemis-instances',
            ],
        },
        'hyperion',
        'deimos',
        'adaptive-learning',
        'user-registration',
        'user-deletion',
        'user-email-uniqueness',
        'jenkins-localvc',
        'saml2-login-registration',
        'oidc-login-registration',
        'troubleshooting',
        'database-tips',
        'known-issues',
        'benchmarking-tool',
        'telemetry',
        'feature-usage',
        'cleanup-service',
        'extensions-setup',
        'tum-live-course-connection',
    ],
};

export default sidebars;
