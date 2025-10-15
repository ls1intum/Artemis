import { themes as prismThemes } from 'prism-react-renderer';
import type { Config } from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

// This runs in Node.js - Don't use client-side code here (browser APIs, JSX...)
const GUIDELINES_REPOSITORY_URL = 'https://github.com/ls1intum/Artemis';
const PAGE_TITLE = 'Artemis Documentation';

const config: Config = {
    title: `Welcome to the ${PAGE_TITLE}`,
    tagline: 'Documentation for the Artemis Learning Management System',
    favicon: 'img/tum-logo-blue.svg',

    customFields: {
        pageTitle: PAGE_TITLE,
    },

    // Future flags, see https://docusaurus.io/docs/api/docusaurus-config#future
    future: {
        v4: true, // Improve compatibility with the upcoming Docusaurus v4
    },

    // Set the production url of your site here
    url: 'https://ls1intum.github.io/',
    // Set the /<baseUrl>/ pathname under which your site is served
    // For GitHub pages deployment, it is often '/<projectName>/'
    baseUrl: '/Artemis/',

    // GitHub pages deployment config.
    // If you aren't using GitHub pages, you don't need these.
    organizationName: 'ls1intum', // Usually your GitHub org/username.
    projectName: 'Artemis', // Usually your repo name.

    onBrokenLinks: 'throw',

    // Even if you don't use internationalization, you can use this field to set
    // useful metadata like html lang. For example, if your site is Chinese, you
    // may want to replace "en" with "zh-Hans".
    i18n: {
        defaultLocale: 'en',
        locales: ['en'],
    },

    presets: [
        [
            'classic',
            {
                docs: false,
                // docs: {
                //     sidebarPath: './sidebars.ts',
                //     // Remove this to remove the "edit this page" links.
                //     editUrl: GUIDELINES_REPOSITORY_URL,
                //     exclude: ['**/README.md'],
                // },
                theme: {
                    customCss: './src/css/custom.css',
                },
            } satisfies Preset.Options,
        ],
    ],

    themes: [
        [
            require.resolve('@easyops-cn/docusaurus-search-local'),
            {
                hashed: true,
                language: ['en'],
            },
        ],
    ],

    plugins: [
        [
            '@docusaurus/plugin-content-docs',
            {
                // id: 'guidelines', // Unique id for this instance
                path: 'guidelines', // Source directory
                routeBasePath: 'guidelines', // URL base path
                sidebarPath: './sidebarsGuidelines.ts', // Path to the sidebar file for this instance
                editUrl: GUIDELINES_REPOSITORY_URL,
                exclude: ['**/README.md'],
            },
        ],
        [
            '@docusaurus/plugin-content-docs',
            {
                id: 'user-manual', // Unique id for this instance
                path: 'user-manual', // Source directory
                routeBasePath: 'user-manual', // URL base path
                sidebarPath: './sidebarsUserManual.ts', // A separate sidebar file
                editUrl: GUIDELINES_REPOSITORY_URL,
            },
        ],
    ],

    themeConfig: {
        // Replace with your project's social card
        image: 'img/tum-logo-blue.svg',
        colorMode: {
            respectPrefersColorScheme: true,
        },
        navbar: {
            title: PAGE_TITLE,
            logo: {
                alt: 'TUM Logo',
                src: 'img/tum-logo-blue.svg',
            },
            items: [
                {
                    type: 'doc',
                    // docsPluginId: 'guidelines', // Corresponds to the id of the plugin instance
                    docId: 'intro', // The ID of the doc to link to (e.g., guidelines/intro.md)
                    position: 'left',
                    label: 'Guidelines',
                },
                {
                    type: 'doc',
                    docsPluginId: 'user-manual', // Corresponds to the id of the plugin instance
                    docId: 'intro', // The ID of the doc to link to (e.g., user-manual/intro.md)
                    position: 'left',
                    label: 'User Manual',
                },
                {
                    href: GUIDELINES_REPOSITORY_URL,
                    label: 'GitHub',
                    position: 'right',
                },
            ],
        },
        footer: {
            style: 'dark',
            links: [
                {
                    title: 'Community',
                    items: [
                        {
                            label: 'AET Website',
                            href: 'https://aet.cit.tum.de/',
                        },
                        {
                            label: 'AET LinkedIn',
                            href: 'https://www.linkedin.com/company/tumaet/posts/?feedView=all',
                        },
                        {
                            label: 'AET Instagram',
                            href: 'https://www.instagram.com/tum.aet/',
                        },
                    ],
                },
                {
                    title: 'More',
                    items: [
                        {
                            label: 'GitHub - UI-UX Guidelines',
                            href: GUIDELINES_REPOSITORY_URL,
                        },
                        {
                            label: 'GitHub - AET Projects',
                            href: 'https://github.com/ls1intum',
                        },
                    ],
                },
                {
                    title: 'Legal',
                    items: [
                        {
                            label: 'Imprint',
                            href: '/imprint',
                        },
                        {
                            label: 'About Us',
                            href: '/about',
                        },
                    ],
                },
            ],
            copyright: `© 2025 Technische Universität München – Built with ❤️ by the UI-UX Cross Project Team at Applied Education Technologies (AET)`,
        },
        prism: {
            theme: prismThemes.github,
            darkTheme: prismThemes.dracula,
        },
    } satisfies Preset.ThemeConfig,
};

export default config;
