import type { SidebarsConfig } from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
    default: [
        'intro',
        'course-management',
        'access-rights',
        'artemis-intelligence',
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
                'production-setup/customization',
                'production-setup/legal-documents',
                'production-setup/additional-tips',
                'production-setup/programming-exercise-adjustments',
                'production-setup/multiple-artemis-instances',
                'production-setup/jenkins-localvc',
            ],
        },
        'hyperion',
        'adaptive-learning',
        'scaling',
        'user-registration',
        'saml2-login-registration',
        'troubleshooting',
        'database-tips',
        'known-issues',
        'benchmarking-tool',
        'telemetry',
        'cleanup-service',
        'extensions-setup',
    ],
};

export default sidebars;
