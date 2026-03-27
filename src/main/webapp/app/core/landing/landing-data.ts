export interface SpotlightStep {
    titleKey: string;
    descriptionKey: string;
    imageSrc: string;
    videoSrc?: string;
    noFrame?: boolean;
}

export interface FeatureCard {
    categoryKey: string;
    descriptionKey: string;
    imageSrc: string;
    imageAlt: string;
    secondaryImageSrc?: string;
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
        imageSrc: 'content/images/landing/iris-logo.png',
        videoSrc: 'content/images/landing/demo-videos/interactive-exercise.webm',
        noFrame: true,
    },
    {
        titleKey: 'landing.spotlight.steps.tutor.title',
        descriptionKey: 'landing.spotlight.steps.tutor.description',
        imageSrc: 'content/images/landing/iris-logo.png',
        videoSrc: 'content/images/landing/demo-videos/iris-demo.webm',
    },
    {
        titleKey: 'landing.spotlight.steps.faq.title',
        descriptionKey: 'landing.spotlight.steps.faq.description',
        imageSrc: 'content/images/landing/iris-logo.png',
    },
    {
        titleKey: 'landing.spotlight.steps.insights.title',
        descriptionKey: 'landing.spotlight.steps.insights.description',
        imageSrc: 'content/images/landing/iris-logo.png',
        videoSrc: 'content/images/landing/demo-videos/adaptive-learning.webm',
    },
];

export const FEATURE_CARDS: FeatureCard[] = [
    {
        categoryKey: 'landing.features.cards.assessment.category',
        descriptionKey: 'landing.features.cards.assessment.description',
        imageSrc: 'content/images/landing/assessment.png',
        imageAlt: 'Assessment dashboard',
    },
    {
        categoryKey: 'landing.features.cards.tutorials.category',
        descriptionKey: 'landing.features.cards.tutorials.description',
        imageSrc: 'content/images/landing/tutorials.png',
        imageAlt: 'Tutorial groups',
    },
    {
        categoryKey: 'landing.features.cards.lectures.category',
        descriptionKey: 'landing.features.cards.lectures.description',
        imageSrc: 'content/images/landing/lectures.png',
        imageAlt: 'Lecture content',
    },
    {
        categoryKey: 'landing.features.cards.examMode.category',
        descriptionKey: 'landing.features.cards.examMode.description',
        imageSrc: 'content/images/landing/exam-mode.png',
        imageAlt: 'Exam mode',
    },
    {
        categoryKey: 'landing.features.cards.communication.category',
        descriptionKey: 'landing.features.cards.communication.description',
        imageSrc: 'content/images/landing/communication.png',
        imageAlt: 'Communication tools',
    },
    {
        categoryKey: 'landing.features.cards.faq.category',
        descriptionKey: 'landing.features.cards.faq.description',
        imageSrc: 'content/images/landing/faq-feature.png',
        imageAlt: 'FAQ management',
    },
    {
        categoryKey: 'landing.features.cards.iris.category',
        descriptionKey: 'landing.features.cards.iris.description',
        imageSrc: 'content/images/landing/iris.png',
        imageAlt: 'Iris AI Tutor',
    },
    {
        categoryKey: 'landing.features.cards.mobileApps.category',
        descriptionKey: 'landing.features.cards.mobileApps.description',
        imageSrc: 'content/images/landing/mobile-phone.png',
        imageAlt: 'Mobile apps',
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
    { name: 'TU Dresden', file: 'content/images/landing/user-logos/dresden.png', width: 123, isWhiteLogo: true },
    { name: 'TU Munich', file: 'content/images/landing/user-logos/tum.png', width: 69 },
    { name: 'AAU Klagenfurt', file: 'content/images/landing/user-logos/aau-logo-300x110-300x110without-backgroung-white-1.png', width: 98, isWhiteLogo: true },
    { name: 'Karlsruhe Institute of Technology', file: 'content/images/landing/user-logos/KIT-Logo.png', width: 100, isWhiteLogo: true },
    { name: 'Hochschule Heilbronn', file: 'content/images/landing/user-logos/Hnn_logo.svg.png', width: 91 },
    { name: 'TU Wien', file: 'content/images/landing/user-logos/technische-universitat-wien-logo-E7B527B95B-seeklogo.com.png', width: 36 },
    { name: 'JKU Linz', file: 'content/images/landing/user-logos/jku.png', width: 60, isWhiteLogo: true },
    { name: 'HM Munich', file: 'content/images/landing/user-logos/hm.png', width: 78 },
    { name: 'University of Innsbruck', file: 'content/images/landing/user-logos/logo-uibk.svg', width: 138 },
    { name: 'University of Salzburg', file: 'content/images/landing/user-logos/uni-sbg-logo-white.png', width: 93, isWhiteLogo: true },
    { name: 'University of Passau', file: 'content/images/landing/user-logos/uni_1200dpi_sw_gross_Grau_Weiss.png', width: 136, isWhiteLogo: true },
    { name: 'University of Stuttgart', file: 'content/images/landing/user-logos/unistuttgart_logo_englisch_cmyk_invertiert.png', width: 160, isWhiteLogo: true },
    { name: 'Maria-Theresia-Gymnasium', file: 'content/images/landing/user-logos/maria-theresia.png', width: 57 },
];

export const FOOTER_LINK_GROUPS: FooterLinkGroup[] = [
    {
        titleKey: 'landing.footer.links.features.title',
        links: [
            { labelKey: 'landing.footer.links.features.exercises', href: 'https://docs.artemis.cit.tum.de/user/exercises/' },
            { labelKey: 'landing.footer.links.features.lectures', href: 'https://docs.artemis.cit.tum.de/user/lectures/' },
            { labelKey: 'landing.footer.links.features.communication', href: 'https://docs.artemis.cit.tum.de/user/communication/' },
            { labelKey: 'landing.footer.links.features.adaptiveLearning', href: 'https://docs.artemis.cit.tum.de/user/adaptive-learning/' },
            { labelKey: 'landing.footer.links.features.iris', href: 'https://docs.artemis.cit.tum.de/user/iris/' },
        ],
    },
    {
        titleKey: 'landing.footer.links.instructors.title',
        links: [
            { labelKey: 'landing.footer.links.instructors.examMode', href: 'https://docs.artemis.cit.tum.de/user/exam_mode/' },
            { labelKey: 'landing.footer.links.instructors.assessment', href: 'https://docs.artemis.cit.tum.de/user/grading/' },
            { labelKey: 'landing.footer.links.instructors.tutorials', href: 'https://docs.artemis.cit.tum.de/user/tutorial-groups/' },
            { labelKey: 'landing.footer.links.instructors.faq', href: 'https://docs.artemis.cit.tum.de/user/faq/' },
        ],
    },
    {
        titleKey: 'landing.footer.links.resources.title',
        links: [
            { labelKey: 'landing.footer.links.resources.documentation', href: 'https://docs.artemis.cit.tum.de' },
            { labelKey: 'landing.footer.links.resources.publication', href: 'https://docs.artemis.tum.de/publications' },
        ],
    },
];
