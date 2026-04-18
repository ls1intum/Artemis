export interface SpotlightStep {
    titleKey: string;
    descriptionKey: string;
    imageSrc: string;
    videoSrc?: string;
}

export interface FeatureCard {
    categoryKey: string;
    descriptionKey: string;
    imageSrc: string;
    imageSrcDark?: string;
    imageAltKey: string;
}

export interface FaqItem {
    questionKey: string;
    answerKey: string;
}

export interface UniversityLogo {
    name: string;
    file: string;
    width: number;
    isWhiteLogo?: boolean;
}

export interface FooterLinkGroup {
    titleKey: string;
    links: { labelKey: string; href?: string; routerLink?: string }[];
}

export const SPOTLIGHT_STEPS: SpotlightStep[] = [
    {
        titleKey: 'landing.spotlight.steps.feedback.title',
        descriptionKey: 'landing.spotlight.steps.feedback.description',
        imageSrc: 'content/images/landing/demo-videos/interactive-exercise-poster.webp',
        videoSrc: 'content/images/landing/demo-videos/interactive-exercise.webm',
    },
    {
        titleKey: 'landing.spotlight.steps.tutor.title',
        descriptionKey: 'landing.spotlight.steps.tutor.description',
        imageSrc: 'content/images/landing/demo-videos/iris-demo-poster.webp',
        videoSrc: 'content/images/landing/demo-videos/iris-demo.webm',
    },
    {
        titleKey: 'landing.spotlight.steps.transcribedLecture.title',
        descriptionKey: 'landing.spotlight.steps.transcribedLecture.description',
        imageSrc: 'content/images/landing/demo-videos/transcribed-lecture-poster.webp',
        videoSrc: 'content/images/landing/demo-videos/transcribed-lecture.webm',
    },
    {
        titleKey: 'landing.spotlight.steps.insights.title',
        descriptionKey: 'landing.spotlight.steps.insights.description',
        imageSrc: 'content/images/landing/demo-videos/adaptive-learning-poster.webp',
        videoSrc: 'content/images/landing/demo-videos/adaptive-learning.webm',
    },
];

export const FEATURE_CARDS: FeatureCard[] = [
    {
        categoryKey: 'landing.features.cards.assessment.category',
        descriptionKey: 'landing.features.cards.assessment.description',
        imageSrc: 'content/images/landing/assessment.webp',
        imageSrcDark: 'content/images/landing/assessment-dark.webp',
        imageAltKey: 'landing.features.cards.assessment.imageAlt',
    },
    {
        categoryKey: 'landing.features.cards.tutorials.category',
        descriptionKey: 'landing.features.cards.tutorials.description',
        imageSrc: 'content/images/landing/tutorials.webp',
        imageSrcDark: 'content/images/landing/tutorials-dark.webp',
        imageAltKey: 'landing.features.cards.tutorials.imageAlt',
    },
    {
        categoryKey: 'landing.features.cards.lectures.category',
        descriptionKey: 'landing.features.cards.lectures.description',
        imageSrc: 'content/images/landing/lectures.webp',
        imageSrcDark: 'content/images/landing/lectures-dark.webp',
        imageAltKey: 'landing.features.cards.lectures.imageAlt',
    },
    {
        categoryKey: 'landing.features.cards.examMode.category',
        descriptionKey: 'landing.features.cards.examMode.description',
        imageSrc: 'content/images/landing/exam-mode.webp',
        imageSrcDark: 'content/images/landing/exam-mode-dark.webp',
        imageAltKey: 'landing.features.cards.examMode.imageAlt',
    },
    {
        categoryKey: 'landing.features.cards.communication.category',
        descriptionKey: 'landing.features.cards.communication.description',
        imageSrc: 'content/images/landing/communication.webp',
        imageSrcDark: 'content/images/landing/communication-dark.webp',
        imageAltKey: 'landing.features.cards.communication.imageAlt',
    },
    {
        categoryKey: 'landing.features.cards.faq.category',
        descriptionKey: 'landing.features.cards.faq.description',
        imageSrc: 'content/images/landing/faq-feature.webp',
        imageSrcDark: 'content/images/landing/faq-feature-dark.webp',
        imageAltKey: 'landing.features.cards.faq.imageAlt',
    },
    {
        categoryKey: 'landing.features.cards.iris.category',
        descriptionKey: 'landing.features.cards.iris.description',
        imageSrc: 'content/images/landing/iris.webp',
        imageSrcDark: 'content/images/landing/iris-dark.webp',
        imageAltKey: 'landing.features.cards.iris.imageAlt',
    },
    {
        categoryKey: 'landing.features.cards.mobileApps.category',
        descriptionKey: 'landing.features.cards.mobileApps.description',
        imageSrc: 'content/images/landing/mobile-phone.webp',
        imageSrcDark: 'content/images/landing/mobile-phone-dark.webp',
        imageAltKey: 'landing.features.cards.mobileApps.imageAlt',
    },
];

export const FAQ_ITEMS: FaqItem[] = [
    {
        questionKey: 'landing.faq.items.free.question',
        answerKey: 'landing.faq.items.free.answer',
    },
    {
        questionKey: 'landing.faq.items.languages.question',
        answerKey: 'landing.faq.items.languages.answer',
    },
    {
        questionKey: 'landing.faq.items.grading.question',
        answerKey: 'landing.faq.items.grading.answer',
    },
    {
        questionKey: 'landing.faq.items.scale.question',
        answerKey: 'landing.faq.items.scale.answer',
    },
    {
        questionKey: 'landing.faq.items.iris.question',
        answerKey: 'landing.faq.items.iris.answer',
    },
    {
        questionKey: 'landing.faq.items.docs.question',
        answerKey: 'landing.faq.items.docs.answer',
    },
];

export const UNIVERSITY_LOGOS: UniversityLogo[] = [
    { name: 'TU Dresden', file: 'content/images/landing/user-logos/dresden.webp', width: 123, isWhiteLogo: true },
    { name: 'TU Munich', file: 'content/images/landing/user-logos/tum.webp', width: 69 },
    { name: 'AAU Klagenfurt', file: 'content/images/landing/user-logos/aau-logo-300x110-300x110without-backgroung-white-1.webp', width: 98, isWhiteLogo: true },
    { name: 'Karlsruhe Institute of Technology', file: 'content/images/landing/user-logos/KIT-Logo.webp', width: 100, isWhiteLogo: true },
    { name: 'Hochschule Heilbronn', file: 'content/images/landing/user-logos/Hnn_logo.svg.webp', width: 91 },
    { name: 'TU Wien', file: 'content/images/landing/user-logos/technische-universitat-wien-logo-E7B527B95B-seeklogo.com.webp', width: 36 },
    { name: 'JKU Linz', file: 'content/images/landing/user-logos/jku.webp', width: 60, isWhiteLogo: true },
    { name: 'HM Munich', file: 'content/images/landing/user-logos/hm.webp', width: 78 },
    { name: 'University of Innsbruck', file: 'content/images/landing/user-logos/logo-uibk.svg', width: 138 },
    { name: 'University of Salzburg', file: 'content/images/landing/user-logos/uni-sbg-logo-white.webp', width: 93, isWhiteLogo: true },
    { name: 'University of Passau', file: 'content/images/landing/user-logos/uni_1200dpi_sw_gross_Grau_Weiss.webp', width: 136, isWhiteLogo: true },
    { name: 'University of Stuttgart', file: 'content/images/landing/user-logos/unistuttgart_logo_englisch_cmyk_invertiert.webp', width: 160, isWhiteLogo: true },
    { name: 'Maria-Theresia-Gymnasium', file: 'content/images/landing/user-logos/maria-theresia.webp', width: 57 },
];

export const FOOTER_LINK_GROUPS: FooterLinkGroup[] = [
    {
        titleKey: 'landing.footer.links.features.title',
        links: [
            { labelKey: 'landing.footer.links.features.exercises', href: 'https://docs.artemis.tum.de/instructor/exercises/intro' },
            { labelKey: 'landing.footer.links.features.lectures', href: 'https://docs.artemis.tum.de/student/learning-content/lectures' },
            { labelKey: 'landing.footer.links.features.communication', href: 'https://docs.artemis.tum.de/student/communication-support/communication' },
            { labelKey: 'landing.footer.links.features.adaptiveLearning', href: 'https://docs.artemis.tum.de/student/progress-analytics/adaptive-learning' },
            { labelKey: 'landing.footer.links.features.iris', href: 'https://ls1intum.github.io/edutelligence/iris/' },
        ],
    },
    {
        titleKey: 'landing.footer.links.instructors.title',
        links: [
            { labelKey: 'landing.footer.links.instructors.examMode', href: 'https://docs.artemis.tum.de/instructor/exams/participation-checker' },
            { labelKey: 'landing.footer.links.instructors.assessment', href: 'https://docs.artemis.tum.de/instructor/assessment-grading/assessment' },
            { labelKey: 'landing.footer.links.instructors.tutorials', href: 'https://docs.artemis.tum.de/instructor/communication-support/tutorial-groups' },
            { labelKey: 'landing.footer.links.instructors.faq', href: 'https://docs.artemis.tum.de/instructor/communication-support/faq' },
        ],
    },
    {
        titleKey: 'landing.footer.links.resources.title',
        links: [
            { labelKey: 'landing.footer.links.resources.documentation', href: 'https://docs.artemis.tum.de' },
            { labelKey: 'landing.footer.links.resources.publication', href: 'https://docs.artemis.tum.de/publications' },
        ],
    },
];
