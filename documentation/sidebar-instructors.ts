import type { SidebarsConfig } from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
    default: [
        'intro',
        {
            type: 'category',
            label: 'Course Management',
            link: {
                type: 'doc',
                id: 'course-management/index',
            },
            items: [
                'course-management/course-requests',
                'course-management/course-configuration',
                'course-management/import-from-course',
                'course-management/exports',
            ],
        },
        {
            type: 'category',
            label: 'Exercises',
            link: {
                type: 'doc',
                id: 'exercises/intro',
            },
            items: [
                'exercises/programming-exercise',
                'exercises/quiz-exercise',
                'exercises/modeling-exercise',
                'exercises/text-exercise',
                'exercises/file-upload-exercise',
                'exercises/team-exercise',
            ],
        },
        {
            type: 'category',
            label: 'Lectures',
            link: {
                type: 'doc',
                id: 'lectures/index',
            },
            items: [
                'lectures/lectures',
                'lectures/lecture-series',
            ],
        },
        {
            type: 'category',
            label: 'Exams',
            link: {
                type: 'doc',
                id: 'exams/intro',
            },
            items: ['exams/exam-timeline', 'exams/participation-checker'],
        },
        {
            type: 'category',
            label: 'Communication & Support',
            link: {
                type: 'doc',
                id: 'communication-support/index',
            },
            items: [
                'communication-support/communication',
                'communication-support/faq',
                'communication-support/tutorial-groups',
            ],
        },
        {
            type: 'category',
            label: 'Assessment & Grading',
            link: {
                type: 'doc',
                id: 'assessment-grading/index',
            },
            items: [
                'assessment-grading/assessment',
                'assessment-grading/grading',
                'assessment-grading/plagiarism-check',
            ],
        },
        {
            type: 'category',
            label: 'Analytics & Adaptive Learning',
            link: {
                type: 'doc',
                id: 'analytics/index',
            },
            items: [
                'analytics/learning-analytics',
                'analytics/adaptive-learning',
            ],
        },
        {
            type: 'category',
            label: 'Integrations',
            link: {
                type: 'doc',
                id: 'integrations/index',
            },
            items: [
                'integrations/integrated-code-lifecycle',
                'integrations/sharing',
                'integrations/lti-configuration',
            ],
        },
    ],
};

export default sidebars;
