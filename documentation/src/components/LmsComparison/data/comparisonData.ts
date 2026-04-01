import { faBrain, faCode, faFileCircleCheck, faComments, faGears, faGauge, faShieldHalved, faRobot, faLock, faClipboardCheck } from '@fortawesome/free-solid-svg-icons';

import { type Feature, type FeatureCategory, type HighlightCardData, type PlatformInfo, PlatformId, SupportLevel } from './types';

const S = SupportLevel.Supported;
const P = SupportLevel.Partial;
const N = SupportLevel.None;

export const platforms: Record<PlatformId, PlatformInfo> = {
    [PlatformId.Artemis]: { id: PlatformId.Artemis, name: 'Artemis', shortName: 'Artemis', color: '#0065BD' },
    [PlatformId.Canvas]: { id: PlatformId.Canvas, name: 'Canvas LMS', shortName: 'Canvas', color: '#E4060F' },
    [PlatformId.Moodle]: { id: PlatformId.Moodle, name: 'Moodle', shortName: 'Moodle', color: '#F98012' },
    [PlatformId.Blackboard]: { id: PlatformId.Blackboard, name: 'Blackboard', shortName: 'Bb', color: '#262626' },
    [PlatformId.ILIAS]: { id: PlatformId.ILIAS, name: 'ILIAS', shortName: 'ILIAS', color: '#005CA9' },
    [PlatformId.OpenOlat]: { id: PlatformId.OpenOlat, name: 'OpenOlat', shortName: 'OpenOlat', color: '#5B9A68' },
};

export const selectablePlatforms: PlatformId[] = [PlatformId.Canvas, PlatformId.Moodle, PlatformId.Blackboard, PlatformId.ILIAS, PlatformId.OpenOlat];

export const defaultSelections: [PlatformId, PlatformId] = [PlatformId.Canvas, PlatformId.Moodle];

export const highlightCards: HighlightCardData[] = [
    {
        icon: faRobot,
        title: 'AI-Powered Learning',
        description: 'Iris, an AI virtual tutor, guides students through exercises. AI-assisted grading reduces feedback time.',
        borderColor: '#0065BD',
    },
    {
        icon: faCode,
        title: 'Built for CS Education',
        description: 'Native programming exercises with automatic grading in 15+ languages. No plugins required.',
        borderColor: '#A2AD00',
    },
    {
        icon: faLock,
        title: 'European Data Sovereignty',
        description: 'Fully self-hosted and open-source. No student data leaves your servers. GDPR-compliant.',
        borderColor: '#E37222',
    },
    {
        icon: faClipboardCheck,
        title: 'Online Exams at Scale',
        description: 'Integrated exam mode with exercise variants and plagiarism detection. Proven with 2,000+ students.',
        borderColor: '#98C6EA',
    },
];

function feature(
    id: string,
    name: string,
    support: [SupportLevel, SupportLevel, SupportLevel, SupportLevel, SupportLevel, SupportLevel],
    options?: { tooltip?: string; notes?: Partial<Record<PlatformId, string>> },
): Feature {
    return {
        id,
        name,
        tooltip: options?.tooltip,
        support: {
            [PlatformId.Artemis]: support[0],
            [PlatformId.Canvas]: support[1],
            [PlatformId.Moodle]: support[2],
            [PlatformId.Blackboard]: support[3],
            [PlatformId.ILIAS]: support[4],
            [PlatformId.OpenOlat]: support[5],
        },
        notes: options?.notes,
    };
}

export const featureCategories: FeatureCategory[] = [
    {
        id: 'ai',
        name: 'AI & Intelligent Learning',
        icon: faBrain,
        features: [
            feature('ai-tutor', 'AI Virtual Tutor', [S, N, N, N, N, N], {
                tooltip: 'AI assistant that guides students through exercises and answers questions based on course content',
            }),
            feature('ai-grading', 'AI-Assisted Grading & Feedback', [S, N, P, P, N, N], {
                notes: {
                    [PlatformId.Moodle]: 'Via third-party plugins',
                    [PlatformId.Blackboard]: 'Limited AI features in Ultra',
                },
            }),
            feature('ai-exercise-gen', 'AI Exercise Generation', [S, N, N, N, N, N], {
                tooltip: 'AI-assisted creation of exercises including problem statements and test cases',
            }),
            feature('adaptive-learning', 'Adaptive Learning Paths', [S, P, N, P, N, N], {
                notes: {
                    [PlatformId.Canvas]: 'Mastery Paths with limited adaptivity',
                    [PlatformId.Blackboard]: 'Adaptive release rules',
                },
            }),
            feature('competency-tracking', 'Competency Tracking', [S, P, P, P, P, P], {
                notes: {
                    [PlatformId.Canvas]: 'Learning Outcomes',
                    [PlatformId.Moodle]: 'Competency framework',
                    [PlatformId.Blackboard]: 'Goals and standards alignment',
                    [PlatformId.ILIAS]: 'Competence management',
                    [PlatformId.OpenOlat]: 'Taxonomy-based competencies',
                },
            }),
        ],
    },
    {
        id: 'exercises',
        name: 'Exercises & Assessment',
        icon: faCode,
        features: [
            feature('programming', 'Programming Exercises (auto-graded)', [S, N, P, N, N, N], {
                tooltip: 'Students write code that is automatically compiled, tested, and graded',
                notes: {
                    [PlatformId.Moodle]: 'CodeRunner plugin',
                },
            }),
            feature('modeling', 'Modeling Exercises (UML)', [S, N, N, N, N, N], {
                tooltip: 'Drag-and-drop UML diagram exercises with semi-automatic assessment',
            }),
            feature('text', 'Text Exercises', [S, S, S, S, S, S]),
            feature('quiz', 'Quiz Exercises', [S, S, S, S, S, S]),
            feature('file-upload', 'File Upload Exercises', [S, S, S, S, S, S]),
            feature('team', 'Team Exercises', [S, N, P, N, P, P], {
                notes: {
                    [PlatformId.Moodle]: 'Group assignments',
                    [PlatformId.ILIAS]: 'Group submissions',
                    [PlatformId.OpenOlat]: 'Group tasks',
                },
            }),
            feature('code-review', 'Automatic Code Review & Static Analysis', [S, N, N, N, N, N], {
                tooltip: 'Automated static analysis feedback on code quality, style, and common errors',
            }),
            feature('plagiarism', 'Plagiarism Detection', [S, P, P, S, P, P], {
                notes: {
                    [PlatformId.Canvas]: 'Turnitin integration',
                    [PlatformId.Moodle]: 'Turnitin / Urkund plugins',
                    [PlatformId.ILIAS]: 'Via plugin',
                    [PlatformId.OpenOlat]: 'Via plugin',
                },
            }),
        ],
    },
    {
        id: 'exams',
        name: 'Exams',
        icon: faFileCircleCheck,
        features: [
            feature('exam-mode', 'Integrated Online Exam Mode', [S, N, P, P, P, S], {
                notes: {
                    [PlatformId.Moodle]: 'Quiz-based exams with restrictions',
                    [PlatformId.Blackboard]: 'Test tool with lockdown browser',
                    [PlatformId.ILIAS]: 'Test & Assessment module',
                },
            }),
            feature('exam-variants', 'Exercise Variants per Student', [S, N, P, P, N, P], {
                tooltip: 'Each student receives a different combination of exercises to prevent cheating',
                notes: {
                    [PlatformId.Moodle]: 'Random questions from question bank',
                    [PlatformId.Blackboard]: 'Random blocks and pools',
                    [PlatformId.OpenOlat]: 'Random selection from pool',
                },
            }),
            feature('exam-review', 'Exam Review Workflow', [S, N, N, N, P, P], {
                tooltip: 'Structured workflow where students review their exam results and can file complaints',
            }),
            feature('test-runs', 'Test Runs for Instructors', [S, N, N, N, N, N], {
                tooltip: 'Instructors can simulate the exam experience before going live',
            }),
        ],
    },
    {
        id: 'communication',
        name: 'Communication & Collaboration',
        icon: faComments,
        features: [
            feature('channels', 'Course Channels & Messaging', [S, S, P, S, P, P], {
                notes: {
                    [PlatformId.Moodle]: 'Basic messaging, no real-time channels',
                    [PlatformId.ILIAS]: 'Mail and forum-based',
                    [PlatformId.OpenOlat]: 'Course chat and messaging',
                },
            }),
            feature('notifications', 'Real-Time Notifications', [S, S, P, S, P, P], {
                notes: {
                    [PlatformId.Moodle]: 'Delayed notification processing',
                    [PlatformId.ILIAS]: 'Email-based notifications',
                    [PlatformId.OpenOlat]: 'Limited push notifications',
                },
            }),
            feature('qa-forums', 'Q&A / Discussion Forums', [S, S, S, S, S, S]),
            feature('mobile', 'Mobile Apps (iOS/Android)', [S, S, S, S, P, P], {
                notes: {
                    [PlatformId.ILIAS]: 'Community-maintained app',
                    [PlatformId.OpenOlat]: 'Mobile-responsive web only',
                },
            }),
        ],
    },
    {
        id: 'course-mgmt',
        name: 'Course Management',
        icon: faGears,
        features: [
            feature('lectures', 'Lecture Management & Slides', [S, P, P, P, S, S], {
                notes: {
                    [PlatformId.Canvas]: 'File-based, no structured lecture units',
                    [PlatformId.Moodle]: 'Resources and labels',
                    [PlatformId.Blackboard]: 'Content areas',
                },
            }),
            feature('tutorial-groups', 'Tutorial Group Management', [S, N, P, N, P, S], {
                notes: {
                    [PlatformId.Moodle]: 'Groups without scheduling',
                    [PlatformId.ILIAS]: 'Groups with session management',
                },
            }),
            feature('grading', 'Grading & Grade Export', [S, S, S, S, S, S]),
            feature('analytics', 'Learning Analytics Dashboard', [S, P, P, P, P, P], {
                notes: {
                    [PlatformId.Canvas]: 'Basic analytics, advanced via paid add-on',
                    [PlatformId.Moodle]: 'Analytics via plugins',
                    [PlatformId.Blackboard]: 'Retention Center',
                    [PlatformId.ILIAS]: 'Limited built-in statistics',
                    [PlatformId.OpenOlat]: 'Basic course statistics',
                },
            }),
        ],
    },
    {
        id: 'quality',
        name: 'Quality Attributes',
        icon: faGauge,
        features: [
            feature('scalability', 'Scalability (concurrent users)', [S, S, S, S, P, P], {
                tooltip: 'Ability to handle large numbers of simultaneous users',
                notes: {
                    [PlatformId.Artemis]: 'Proven with 2,000+ concurrent students',
                    [PlatformId.ILIAS]: 'Requires careful tuning for large deployments',
                    [PlatformId.OpenOlat]: 'Suitable for mid-size deployments',
                },
            }),
            feature('realtime', 'Performance / Real-Time Updates', [S, P, P, P, N, N], {
                tooltip: 'Live updates without page refresh (e.g., submission results, messages)',
                notes: {
                    [PlatformId.Canvas]: 'Some real-time features',
                    [PlatformId.Moodle]: 'Polling-based updates',
                    [PlatformId.Blackboard]: 'Limited real-time in Ultra',
                },
            }),
            feature('ux', 'User Experience / Modern UI', [S, S, P, P, P, P], {
                notes: {
                    [PlatformId.Moodle]: 'Dated UI, improving with Moodle 4.x',
                    [PlatformId.Blackboard]: 'Ultra interface is modern, Original is dated',
                    [PlatformId.ILIAS]: 'Functional but dated interface',
                    [PlatformId.OpenOlat]: 'Functional, utilitarian design',
                },
            }),
            feature('accessibility', 'Accessibility', [P, S, P, S, P, P], {
                tooltip: 'Compliance with WCAG accessibility standards',
                notes: {
                    [PlatformId.Artemis]: 'Improving, not yet fully WCAG compliant',
                    [PlatformId.Canvas]: 'Strong VPAT, WCAG 2.1 AA',
                    [PlatformId.Blackboard]: 'Ally integration, VPAT available',
                },
            }),
        ],
    },
    {
        id: 'security',
        name: 'Security, Privacy & Deployment',
        icon: faShieldHalved,
        features: [
            feature('self-hosted', 'Self-Hosted Deployment', [S, N, S, N, S, S], {
                notes: {
                    [PlatformId.Canvas]: 'SaaS only (Instructure-hosted)',
                    [PlatformId.Blackboard]: 'SaaS only (Anthology-hosted)',
                },
            }),
            feature('gdpr', 'GDPR Compliance', [S, P, S, P, S, S], {
                notes: {
                    [PlatformId.Canvas]: 'US-based provider, EU data centers available',
                    [PlatformId.Blackboard]: 'US-based provider, DPA available',
                },
            }),
            feature('open-source', 'Open-Source License', [S, N, S, N, S, S]),
            feature('sso', 'SSO / SAML / LDAP', [S, S, S, S, S, S]),
            feature('lti', 'LTI Integration', [S, S, S, S, S, S]),
            feature('passkey', 'Passkey / WebAuthn Support', [S, N, N, N, N, N], {
                tooltip: 'Phishing-resistant authentication using FIDO2/WebAuthn passkeys',
            }),
        ],
    },
];
