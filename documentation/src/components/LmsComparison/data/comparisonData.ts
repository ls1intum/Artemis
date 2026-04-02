import {
    faBrain,
    faCode,
    faFileCircleCheck,
    faComments,
    faGears,
    faGauge,
    faShieldHalved,
    faRobot,
    faLock,
    faClipboardCheck,
    faGlobe,
    faPuzzlePiece,
} from '@fortawesome/free-solid-svg-icons';

import { type Feature, type FeatureCategory, type HighlightCardData, type PlatformInfo, PlatformId, SupportLevel } from './types';

const S = SupportLevel.Supported;
const P = SupportLevel.Partial;
const N = SupportLevel.None;

export const platforms: Record<PlatformId, PlatformInfo> = {
    [PlatformId.Artemis]: { id: PlatformId.Artemis, name: 'Artemis' },
    [PlatformId.Canvas]: { id: PlatformId.Canvas, name: 'Canvas LMS' },
    [PlatformId.Moodle]: { id: PlatformId.Moodle, name: 'Moodle' },
    [PlatformId.Blackboard]: { id: PlatformId.Blackboard, name: 'Blackboard' },
    [PlatformId.ILIAS]: { id: PlatformId.ILIAS, name: 'ILIAS' },
    [PlatformId.OpenOlat]: { id: PlatformId.OpenOlat, name: 'OpenOlat' },
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

/** @param support Support levels in order: [Artemis, Canvas, Moodle, Blackboard, ILIAS, OpenOlat] */
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
            feature('competency-tracking', 'Competency-based Learning', [S, P, P, P, P, S], {
                notes: {
                    [PlatformId.Canvas]: 'Learning Outcomes',
                    [PlatformId.Moodle]: 'Competency framework',
                    [PlatformId.Blackboard]: 'Goals and standards alignment',
                    [PlatformId.ILIAS]: 'Competence management',
                    [PlatformId.OpenOlat]: 'Competency frameworks with curriculum mapping',
                },
            }),
            feature('constructive-alignment', 'Constructive Alignment', [S, P, P, P, P, P], {
                tooltip: 'Learning objectives, learning activities, and assessments are deliberately linked',
                notes: {
                    [PlatformId.Artemis]: 'Exercises and integrated online exams aligned with learning objectives',
                    [PlatformId.Canvas]: 'Outcomes can be linked to assignments',
                    [PlatformId.Moodle]: 'Competencies linkable to activities',
                    [PlatformId.Blackboard]: 'Goals and standards mapping',
                    [PlatformId.ILIAS]: 'Competencies linked to learning modules',
                    [PlatformId.OpenOlat]: 'Taxonomy-based tagging of resources',
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
            feature('quiz', 'Quiz Exercises', [S, S, S, S, S, S], {
                notes: {
                    [PlatformId.Moodle]: '30+ question types including calculated variants',
                    [PlatformId.ILIAS]: 'IMS QTI compliant question engine',
                },
            }),
            feature('file-upload', 'File Upload Exercises', [S, S, S, S, S, S]),
            feature('team', 'Team Exercises', [S, N, P, N, P, P], {
                notes: {
                    [PlatformId.Moodle]: 'Group assignments',
                    [PlatformId.ILIAS]: 'Group submissions',
                    [PlatformId.OpenOlat]: 'Group tasks',
                },
            }),
            feature('code-review', 'Automatic Code Review', [S, N, N, N, N, N], {
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
            feature('inline-grading', 'Inline Document Grading', [N, S, P, S, P, P], {
                tooltip: 'Annotate and grade PDFs, documents, and media submissions inline',
                notes: {
                    [PlatformId.Canvas]: 'SpeedGrader with annotation',
                    [PlatformId.Moodle]: 'PDF annotation in assignment grading',
                    [PlatformId.Blackboard]: 'Inline document annotation',
                    [PlatformId.ILIAS]: 'Basic annotation support',
                    [PlatformId.OpenOlat]: 'Basic annotation support',
                },
            }),
            feature('double-blind-grading', 'Double-Blind Grading', [S, P, P, P, N, P], {
                tooltip: 'Anonymized assessment where neither grader nor student identity is revealed',
                notes: {
                    [PlatformId.Canvas]: 'Anonymous grading (single-blind)',
                    [PlatformId.Moodle]: 'Blind marking for assignments',
                    [PlatformId.Blackboard]: 'Anonymous grading option',
                    [PlatformId.OpenOlat]: 'Anonymous grading option',
                },
            }),
            feature('assessment-training', 'Assessment Training', [S, N, N, N, N, N], {
                tooltip: 'Tutors practice grading on example submissions to calibrate before assessing real student work',
            }),
            feature('quiz-training', 'Quiz Practice Mode with Leaderboard', [S, N, N, N, N, N], {
                tooltip: 'Students practice quizzes repeatedly with a competitive leaderboard for motivation',
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
            feature('faqs', 'FAQs', [S, N, P, N, P, P], {
                tooltip: 'Course-specific FAQ management for frequently asked questions',
                notes: {
                    [PlatformId.Moodle]: 'Via wiki or custom pages',
                    [PlatformId.ILIAS]: 'FAQ page object',
                    [PlatformId.OpenOlat]: 'Via wiki or info pages',
                },
            }),
            feature('mobile', 'Mobile Apps (iOS/Android)', [S, S, S, S, P, P], {
                notes: {
                    [PlatformId.Canvas]: 'Native apps with offline support',
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
            feature('analytics', 'Learning Analytics Dashboard', [S, P, P, S, P, P], {
                notes: {
                    [PlatformId.Canvas]: 'Basic analytics, advanced via paid add-on',
                    [PlatformId.Moodle]: 'Analytics via plugins',
                    [PlatformId.Blackboard]: 'Anthology Illuminate for institutional analytics',
                    [PlatformId.ILIAS]: 'Limited built-in statistics',
                    [PlatformId.OpenOlat]: 'Basic course statistics',
                },
            }),
            feature('curriculum', 'Curriculum Management', [N, N, P, N, P, S], {
                tooltip: 'Multi-level curriculum structures with program, semester, and module hierarchy',
                notes: {
                    [PlatformId.Moodle]: 'Limited via course categories',
                    [PlatformId.ILIAS]: 'Category and repository structure',
                    [PlatformId.OpenOlat]: 'Full curriculum management with automatic enrollment',
                },
            }),
            feature('eportfolio', 'ePortfolio', [N, N, P, N, S, S], {
                tooltip: 'Students collect, reflect on, and present learning artifacts',
                notes: {
                    [PlatformId.Moodle]: 'Mahara integration or basic competency portfolios',
                    [PlatformId.ILIAS]: 'Built-in competency-based portfolio',
                    [PlatformId.OpenOlat]: 'Integrated portfolio with competency mapping',
                },
            }),
        ],
    },
    {
        id: 'content',
        name: 'Content & Standards',
        icon: faGlobe,
        features: [
            feature('content-authoring', 'Built-in Content Authoring', [S, P, S, P, S, S], {
                tooltip: 'Create rich learning content directly within the platform',
                notes: {
                    [PlatformId.Artemis]: 'Markdown-based — lightweight, version-control friendly',
                    [PlatformId.Canvas]: 'Rich text editor for pages',
                    [PlatformId.Moodle]: 'Lesson builder, book, wiki modules',
                    [PlatformId.Blackboard]: 'Content editor',
                    [PlatformId.ILIAS]: 'Learning module editor with page layout',
                    [PlatformId.OpenOlat]: 'Course editor with content elements',
                },
            }),
            feature('video-transcription', 'Automatic Video Transcription', [S, P, P, S, N, P], {
                tooltip: 'Automatically generate captions and transcripts for lecture videos',
                notes: {
                    [PlatformId.Canvas]: 'Via third-party integrations (e.g., 3Play Media)',
                    [PlatformId.Moodle]: 'Via plugins or external tools',
                    [PlatformId.Blackboard]: 'Ally auto-generates alternative formats including captions',
                    [PlatformId.OpenOlat]: 'Via external transcription services',
                },
            }),
            feature('multilingual', 'Multilingual Interface', [P, S, S, S, S, S], {
                tooltip: 'Platform interface available in multiple languages',
                notes: {
                    [PlatformId.Artemis]: 'English and German',
                    [PlatformId.Moodle]: '100+ language packs',
                },
            }),
            feature('scorm', 'SCORM / xAPI / cmi5', [N, S, S, S, S, S], {
                tooltip: 'Support for standardized learning content packages',
                notes: {
                    [PlatformId.ILIAS]: 'Strongest open-source SCORM 2004 implementation',
                },
            }),
            feature('h5p', 'Interactive Content (H5P)', [N, P, S, P, P, P], {
                tooltip: 'Create interactive content like quizzes, presentations, and simulations',
                notes: {
                    [PlatformId.Canvas]: 'Via LTI integration',
                    [PlatformId.Moodle]: 'Native H5P integration',
                    [PlatformId.Blackboard]: 'Via LTI integration',
                    [PlatformId.ILIAS]: 'H5P plugin',
                    [PlatformId.OpenOlat]: 'H5P plugin',
                },
            }),
        ],
    },
    {
        id: 'extensibility',
        name: 'Extensibility & Ecosystem',
        icon: faPuzzlePiece,
        features: [
            feature('plugin-ecosystem', 'Plugin / Extension Ecosystem', [N, S, S, P, P, P], {
                tooltip: 'Marketplace or repository of third-party extensions',
                notes: {
                    [PlatformId.Canvas]: '500+ LTI-certified integrations',
                    [PlatformId.Moodle]: '2,000+ community plugins',
                    [PlatformId.Blackboard]: 'Building Blocks and LTI partners',
                    [PlatformId.ILIAS]: 'Community plugin repository',
                    [PlatformId.OpenOlat]: 'Limited extension ecosystem',
                },
            }),
            feature('video-conferencing', 'Built-in Video Conferencing', [N, P, P, S, P, P], {
                tooltip: 'Integrated virtual classroom or video meeting capability',
                notes: {
                    [PlatformId.Canvas]: 'BigBlueButton / Zoom LTI',
                    [PlatformId.Moodle]: 'BigBlueButton integration',
                    [PlatformId.Blackboard]: 'Collaborate Ultra built-in',
                    [PlatformId.ILIAS]: 'BigBlueButton plugin',
                    [PlatformId.OpenOlat]: 'BigBlueButton / Teams integration',
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
            feature('accessibility', 'Accessibility (WCAG)', [P, S, P, S, P, P], {
                tooltip: 'Compliance with WCAG accessibility standards and built-in accessibility tools',
                notes: {
                    [PlatformId.Artemis]: 'Improving, not yet fully WCAG compliant',
                    [PlatformId.Canvas]: 'VPAT-certified, WCAG 2.1 AA',
                    [PlatformId.Blackboard]: 'Ally tool for automated accessibility scoring, VPAT available',
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
