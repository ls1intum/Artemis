import type { SidebarsConfig } from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
    default: [
        'intro',
        {
            type: 'category',
            label: 'Getting Started',
            link: { type: 'doc', id: 'getting-started/index' },
            items: [
                'getting-started/general',
                'getting-started/courses',
                'getting-started/user-experience',
                'getting-started/mobile-applications',
            ],
        },
        {
            type: 'category',
            label: 'Exercises',
            link: { type: 'doc', id: 'exercises/intro' },
            items: [
                'exercises/quiz-exercise',
                'exercises/modeling-exercise',
                'exercises/programming-exercise',
                'exercises/quiz-training',
            ],
        },
        {
            type: 'category',
            label: 'Lectures & AI Tutor',
            link: { type: 'doc', id: 'learning-content/index' },
            items: [
                'learning-content/lectures',
                'learning-content/iris',
            ],
        },
        'exams',
        {
            type: 'category',
            label: 'Assessment & Grades',
            link: { type: 'doc', id: 'assessment-grades/index' },
            items: [
                'assessment-grades/assessment',
                'assessment-grades/grading',
                'assessment-grades/plagiarism-check',
            ],
        },
        {
            type: 'category',
            label: 'Communication & Support',
            link: { type: 'doc', id: 'communication-support/index' },
            items: [
                'communication-support/communication',
                'communication-support/notifications',
                'communication-support/faq',
                'communication-support/tutorial-groups',
            ],
        },
        {
            type: 'category',
            label: 'Progress & Analytics',
            link: { type: 'doc', id: 'progress-analytics/index' },
            items: [
                'progress-analytics/learning-analytics',
                'progress-analytics/adaptive-learning',
                'progress-analytics/calendar',
            ],
        },
        {
            type: 'category',
            label: 'Tools & Reference',
            link: { type: 'doc', id: 'tools-reference/index' },
            items: [
                'tools-reference/markdown-support',
                'tools-reference/integrated-code-lifecycle',
                'tools-reference/exports',
            ],
        },
    ],
};

export default sidebars;
