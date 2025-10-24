import { themes as prismThemes } from 'prism-react-renderer';
import type { Config } from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

// This runs in Node.js - Don't use client-side code here (browser APIs, JSX...)
const ARTEMIS_DOCUMENTATION_REPOSITORY_URL = 'https://github.com/ls1intum/Artemis/documentation';
const PAGE_TITLE = 'Artemis Documentation';

const config: Config = {
    title: `Welcome to the ${PAGE_TITLE}`,
    tagline: 'Documentation for the Artemis Learning Management System',
    favicon: 'img/artemis-favicon.svg',

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
                theme: {
                    customCss: './src/css/custom.css',
                },
            } satisfies Preset.Options,
        ],
    ],

    themes: [
        [
            require.resolve('@easyops-cn/docusaurus-search-local'),
            /** @type {import("@easyops-cn/docusaurus-search-local").PluginOptions} */
            ({
                hashed: true,
                language: ['en'],
                indexDocs: true,
                docsRouteBasePath: ['student', 'instructor', 'staff', 'admin'],
                searchContextByPaths: [
                    {
                        label: 'Student Guide',
                        path: 'student'
                    },
                    {
                        label: 'Instructor Guide',
                        path: 'instructor'
                    },
                    {
                        label: 'Staff',
                        path: 'staff'
                    },
                    {
                        label: 'Admin',
                        path: 'admin'
                    }
                ],
                hideSearchBarWithNoSearchContext: true,
            }),
        ],
    ],

    plugins: [
        [
            '@docusaurus/plugin-content-docs',
            {
                path: 'docs/student',
                routeBasePath: 'student',
                sidebarPath: './sidebars.ts',
                editUrl: ARTEMIS_DOCUMENTATION_REPOSITORY_URL,
                exclude: ['**/README.md'],
            },
        ],
        [
            '@docusaurus/plugin-content-docs',
            {
                id: 'instructor',
                path: 'docs/instructor',
                routeBasePath: 'instructor',
                sidebarPath: './sidebars.ts',
                editUrl: ARTEMIS_DOCUMENTATION_REPOSITORY_URL,
            },
        ],
        [
            '@docusaurus/plugin-content-docs',
            {
                id: 'staff',
                path: 'docs/staff',
                routeBasePath: 'staff',
                sidebarPath: './sidebars.ts',
                editUrl: ARTEMIS_DOCUMENTATION_REPOSITORY_URL,
            },
        ],
        [
            '@docusaurus/plugin-content-docs',
            {
                id: 'admin',
                path: 'docs/admin',
                routeBasePath: 'admin',
                sidebarPath: './sidebars.ts',
                editUrl: ARTEMIS_DOCUMENTATION_REPOSITORY_URL,
            },
        ],
    ],

    themeConfig: {
        // Replace with your project's social card
        image: 'img/artemis-favicon.svg',
        colorMode: {
            respectPrefersColorScheme: true,
        },
        navbar: {
            title: PAGE_TITLE,
            logo: {
                alt: 'TUM Logo',
                src: 'img/artemis-favicon.svg',
            },
            items: [
                {
                    href: ARTEMIS_DOCUMENTATION_REPOSITORY_URL,
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
                            label: 'GitHub - Artemis',
                            href: ARTEMIS_DOCUMENTATION_REPOSITORY_URL,
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
            copyright: `© 2025 Technische Universität München – Built with ❤️ by the Artemis Team at Applied Education Technologies (AET)`,
        },
        prism: {
            theme: prismThemes.github,
            darkTheme: prismThemes.dracula,
        },
    },
} as Config;

export default config;
