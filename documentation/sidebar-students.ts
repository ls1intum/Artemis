import type { SidebarsConfig } from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
    default: [
        'intro',
        'general',
        'courses',
        {
            type: 'category',
            label: 'Exercises',
            link: {
                type: 'doc',
                id: 'exercises/intro',
            },
            items: ['exercises/quiz-exercise', 'exercises/modeling-exercise'],
        },
        'lectures',
        'assessment',
        'quiz-training',
        'exams',
        'calendar',
        'user-experience',
        'notifications',
        'faq',
        'grading',
        'integrated-code-lifecycle',
        'tutorial-groups',
        'learning-analytics',
        'adaptive-learning',
        'mobile-applications',
        'plagiarism-check',
        'markdown-support',
        'exports',
    ],
};

export default sidebars;
