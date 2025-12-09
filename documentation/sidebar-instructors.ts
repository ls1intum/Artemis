import type { SidebarsConfig } from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
    default: [
        'intro',
        {
            type: 'category',
            label: 'Exercises',
            link: {
                type: 'doc',
                id: 'exercises/intro'
            },
            items: [
                'exercises/textual-exercise',
                'exercises/file-upload-exercise',
            ]
        },
        'lecture-series',
        'assessment',
        'faq',
        'tutorial-groups',
        'plagiarism-check'
    ],
};

export default sidebars;
