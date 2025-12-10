import type { SidebarsConfig } from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
    default: [
        'intro',
        'programming',
        'lecture-series',
        'assessment',
        {
            type: 'category',
            label: 'Exams',
            link: {
                type: 'doc',
                id: 'exams/intro'
            },
            items: [
                'exams/exam-timeline',
                'exams/participation-checker',
            ]
        },
        'faq',
        'tutorial-groups',
        'plagiarism-check'
    ],
};

export default sidebars;
