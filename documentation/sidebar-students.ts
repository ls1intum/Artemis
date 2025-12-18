import type { SidebarsConfig } from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
    default: [
        'intro',
        'general',
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
            ]
        },
        'assessment',
        'exams',
        'quiz-training',
        'calendar',
        'faq',
        'tutorial-groups',
        'plagiarism-check',
        'notifications',
        'user-experience',
    ],
};

export default sidebars;
