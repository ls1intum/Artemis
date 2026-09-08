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
                'course-management/user-management',
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
                {
                    type: 'category',
                    label: 'Programming Exercise',
                    link: {
                        type: 'doc',
                        id: 'exercises/programming-exercise/index',
                    },
                    items: [
                        'exercises/programming-exercise/create-an-exercise',
                        'exercises/programming-exercise/write-code-and-tests',
                        'exercises/programming-exercise/configure-the-build',
                        'exercises/programming-exercise/grade-and-verify',
                        'exercises/programming-exercise/ai-assisted-authoring',
                        'exercises/hyperion-generation',
                        'exercises/programming-exercise/static-analysis-rules',
                        'exercises/programming-exercise/repository-access',
                    ],
                },
                'exercises/consistency-check',
                'exercises/quiz-exercise',
                'exercises/modeling-exercise',
                'exercises/text-exercise',
                'exercises/file-upload-exercise',
                'exercises/team-exercise',
                'exercises/deimos',
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
            items: [
                'exams/create-an-exam',
                'exams/add-exercises',
                'exams/prepare-and-test',
                'exams/conduct-an-exam',
                'exams/assess-an-exam',
                'exams/grade-and-publish',
                'exams/review-and-complaints',
                'exams/participation-checker',
            ],
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
                'communication-support/iris',
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
                'assessment-grading/ai-feedback',
                'assessment-grading/plagiarism-check',
                'assessment-grading/deimos',
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
                'integrations/deimos',
            ],
        },
    ],
};

export default sidebars;
