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
        'plagiarism-check',
        'courses',
        'exports',
        'grading',
    ],
};

export default sidebars;
