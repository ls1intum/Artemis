export interface SpotlightStep {
    titleKey: string;
    descriptionKey: string;
    imageSrc: string;
    videoSrc?: string;
}

export interface FeatureCardDownloadLink {
    href: string;
    badgeSrc: string;
    badgeSrcDark: string;
    altKey: string;
    disabled?: boolean;
}

export interface FeatureCard {
    categoryKey: string;
    descriptionKey: string;
    imageSrc: string;
    imageSrcDark?: string;
    imageAltKey: string;
    downloadLinks?: FeatureCardDownloadLink[];
}

export interface FaqItem {
    questionKey: string;
    answerKey: string;
}

export interface Pillar {
    titleKey: string;
    descriptionKey: string;
}

export interface ResearchStep {
    titleKey: string;
    descriptionKey: string;
}

/** A card that links out to the authoritative documentation for one topic. */
export interface LinkCard {
    titleKey: string;
    descriptionKey: string;
    href: string;
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
        downloadLinks: [
            {
                href: 'https://apps.apple.com/app/artemis-learning/id6478965616',
                badgeSrc: 'content/images/landing/badges/app-store-badge.svg',
                badgeSrcDark: 'content/images/landing/badges/app-store-badge-dark.svg',
                altKey: 'landing.features.cards.mobileApps.appStoreAlt',
            },
            {
                href: 'https://play.google.com/store/apps/details?id=de.tum.cit.aet.artemis',
                badgeSrc: 'content/images/landing/badges/google-play-badge.svg',
                badgeSrcDark: 'content/images/landing/badges/google-play-badge-dark.svg',
                altKey: 'landing.features.cards.mobileApps.playStoreAlt',
            },
        ],
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

/**
 * The four capability pillars Artemis is positioned around. They intentionally stay at a conceptual
 * level; the feature cards below them show what that looks like in the product.
 */
export const PILLARS: Pillar[] = [
    {
        titleKey: 'landing.pillars.items.feedback.title',
        descriptionKey: 'landing.pillars.items.feedback.description',
    },
    {
        titleKey: 'landing.pillars.items.assessment.title',
        descriptionKey: 'landing.pillars.items.assessment.description',
    },
    {
        titleKey: 'landing.pillars.items.adaptive.title',
        descriptionKey: 'landing.pillars.items.adaptive.description',
    },
    {
        titleKey: 'landing.pillars.items.infrastructure.title',
        descriptionKey: 'landing.pillars.items.infrastructure.description',
    },
];

/**
 * Entry points for institutional decision makers. Each card links to the authoritative source
 * instead of repeating it, so there is exactly one place to keep up to date.
 */
export const TRUST_LINKS: LinkCard[] = [
    {
        titleKey: 'landing.trust.items.security.title',
        descriptionKey: 'landing.trust.items.security.description',
        href: 'https://github.com/ls1intum/Artemis/blob/develop/SECURITY.md',
    },
    {
        titleKey: 'landing.trust.items.privacy.title',
        descriptionKey: 'landing.trust.items.privacy.description',
        href: 'https://docs.artemis.tum.de/about/trust#privacy-and-data-protection',
    },
    {
        titleKey: 'landing.trust.items.ai.title',
        descriptionKey: 'landing.trust.items.ai.description',
        href: 'https://docs.artemis.tum.de/admin/artemis-intelligence',
    },
    {
        titleKey: 'landing.trust.items.accessibility.title',
        descriptionKey: 'landing.trust.items.accessibility.description',
        href: 'https://docs.artemis.tum.de/about/trust#accessibility',
    },
    {
        titleKey: 'landing.trust.items.releases.title',
        descriptionKey: 'landing.trust.items.releases.description',
        href: 'https://docs.artemis.tum.de/about/releases',
    },
    {
        titleKey: 'landing.trust.items.governance.title',
        descriptionKey: 'landing.trust.items.governance.description',
        href: 'https://docs.artemis.tum.de/about/governance',
    },
];

/** The loop from a research question to a released capability. */
export const RESEARCH_STEPS: ResearchStep[] = [
    {
        titleKey: 'landing.research.steps.research.title',
        descriptionKey: 'landing.research.steps.research.description',
    },
    {
        titleKey: 'landing.research.steps.prototype.title',
        descriptionKey: 'landing.research.steps.prototype.description',
    },
    {
        titleKey: 'landing.research.steps.evaluation.title',
        descriptionKey: 'landing.research.steps.evaluation.description',
    },
    {
        titleKey: 'landing.research.steps.product.title',
        descriptionKey: 'landing.research.steps.product.description',
    },
];

export const COMMUNITY_LINKS: LinkCard[] = [
    {
        titleKey: 'landing.community.items.github.title',
        descriptionKey: 'landing.community.items.github.description',
        href: 'https://github.com/ls1intum/Artemis',
    },
    {
        titleKey: 'landing.community.items.contributing.title',
        descriptionKey: 'landing.community.items.contributing.description',
        href: 'https://github.com/ls1intum/Artemis/blob/develop/CONTRIBUTING.md',
    },
    {
        titleKey: 'landing.community.items.process.title',
        descriptionKey: 'landing.community.items.process.description',
        href: 'https://docs.artemis.tum.de/developer/open-source',
    },
    {
        titleKey: 'landing.community.items.community.title',
        descriptionKey: 'landing.community.items.community.description',
        href: 'https://docs.artemis.tum.de/about/community',
    },
];

/**
 * Logos shown in the social-proof strip.
 *
 * The canonical adoption list lives in the documentation
 * (`documentation/src/components/Adoption/data/adopters.ts`) and is published at
 * https://docs.artemis.tum.de/about/adoption. This array is the visual subset of it: the Angular build
 * cannot import from the documentation workspace, so the two are kept in sync by hand. When an
 * institution is added to or removed from the canonical list, update this array as well.
 */
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
        titleKey: 'landing.footer.links.project.title',
        links: [
            { labelKey: 'landing.footer.links.project.about', href: 'https://docs.artemis.tum.de/about' },
            { labelKey: 'landing.footer.links.project.try', href: 'https://docs.artemis.tum.de/about/try' },
            { labelKey: 'landing.footer.links.project.trust', href: 'https://docs.artemis.tum.de/about/trust' },
            { labelKey: 'landing.footer.links.project.governance', href: 'https://docs.artemis.tum.de/about/governance' },
            { labelKey: 'landing.footer.links.project.roadmap', href: 'https://docs.artemis.tum.de/about/roadmap' },
            { labelKey: 'landing.footer.links.project.adoption', href: 'https://docs.artemis.tum.de/about/adoption' },
        ],
    },
    {
        titleKey: 'landing.footer.links.resources.title',
        links: [
            { labelKey: 'landing.footer.links.resources.documentation', href: 'https://docs.artemis.tum.de' },
            { labelKey: 'landing.footer.links.resources.publication', href: 'https://docs.artemis.tum.de/publications' },
            { labelKey: 'landing.footer.links.resources.github', href: 'https://github.com/ls1intum/Artemis' },
            { labelKey: 'landing.footer.links.resources.security', href: 'https://github.com/ls1intum/Artemis/blob/develop/SECURITY.md' },
        ],
    },
];
