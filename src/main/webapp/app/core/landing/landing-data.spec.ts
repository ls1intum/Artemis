import { describe, expect, it } from 'vitest';
import { FOOTER_LINK_GROUPS } from './landing-data';

// Public footer model: labels, destinations and order are intentionally preserved when its source representation changes.
const expectedFooterGroups = [
    {
        titleKey: 'landing.footer.links.features.title',
        links: [
            {
                labelKey: 'landing.footer.links.features.exercises',
                href: 'https://docs.artemis.tum.de/instructor/exercises/intro',
            },
            {
                labelKey: 'landing.footer.links.features.lectures',
                href: 'https://docs.artemis.tum.de/student/learning-content/lectures',
            },
            {
                labelKey: 'landing.footer.links.features.communication',
                href: 'https://docs.artemis.tum.de/student/communication-support/communication',
            },
            {
                labelKey: 'landing.footer.links.features.adaptiveLearning',
                href: 'https://docs.artemis.tum.de/student/progress-analytics/adaptive-learning',
            },
            {
                labelKey: 'landing.footer.links.features.iris',
                href: 'https://ls1intum.github.io/edutelligence/iris/',
            },
        ],
    },
    {
        titleKey: 'landing.footer.links.instructors.title',
        links: [
            {
                labelKey: 'landing.footer.links.instructors.examMode',
                href: 'https://docs.artemis.tum.de/instructor/exams/participation-checker',
            },
            {
                labelKey: 'landing.footer.links.instructors.assessment',
                href: 'https://docs.artemis.tum.de/instructor/assessment-grading/assessment',
            },
            {
                labelKey: 'landing.footer.links.instructors.tutorials',
                href: 'https://docs.artemis.tum.de/instructor/communication-support/tutorial-groups',
            },
            {
                labelKey: 'landing.footer.links.instructors.faq',
                href: 'https://docs.artemis.tum.de/instructor/communication-support/faq',
            },
        ],
    },
    {
        titleKey: 'landing.footer.links.project.title',
        links: [
            {
                labelKey: 'landing.footer.links.project.about',
                href: 'https://docs.artemis.tum.de/about',
            },
            {
                labelKey: 'landing.footer.links.project.try',
                href: 'https://docs.artemis.tum.de/about/try',
            },
            {
                labelKey: 'landing.footer.links.project.trust',
                href: 'https://docs.artemis.tum.de/about/trust',
            },
            {
                labelKey: 'landing.footer.links.project.governance',
                href: 'https://docs.artemis.tum.de/about/governance',
            },
            {
                labelKey: 'landing.footer.links.project.roadmap',
                href: 'https://docs.artemis.tum.de/about/roadmap',
            },
            {
                labelKey: 'landing.footer.links.project.adoption',
                href: 'https://docs.artemis.tum.de/about/adoption',
            },
        ],
    },
    {
        titleKey: 'landing.footer.links.resources.title',
        links: [
            {
                labelKey: 'landing.footer.links.resources.documentation',
                href: 'https://docs.artemis.tum.de',
            },
            {
                labelKey: 'landing.footer.links.resources.publication',
                href: 'https://docs.artemis.tum.de/publications',
            },
            {
                labelKey: 'landing.footer.links.resources.github',
                href: 'https://github.com/ls1intum/Artemis',
            },
            {
                labelKey: 'landing.footer.links.resources.security',
                href: 'https://github.com/ls1intum/Artemis/blob/develop/.github/SECURITY.md',
            },
        ],
    },
];

describe('landing footer links', () => {
    it('preserves the published labels, destinations and display order', () => {
        expect(FOOTER_LINK_GROUPS).toEqual(expectedFooterGroups);
    });
});
