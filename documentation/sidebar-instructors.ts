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
                'exercises/quiz-exercise',
                'exercises/modeling-exercise',
                'exercises/textual-exercise',
                'exercises/file-upload-exercise',
                'exercises/team-exercise',
            ]
        },
        'lectures',
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
        'integrated-code-lifecycle',
        'learning-analytics',
        'adaptive-learning',
        'sharing',
        'lti-configuration',
    ],
};

export default sidebars;
