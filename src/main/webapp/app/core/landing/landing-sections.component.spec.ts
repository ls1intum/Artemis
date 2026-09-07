import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LandingCommunityComponent } from 'app/core/landing/landing-community.component';
import { LandingFooterComponent } from 'app/core/landing/landing-footer.component';
import { LandingHeroComponent } from 'app/core/landing/landing-hero.component';
import { LandingPillarsComponent } from 'app/core/landing/landing-pillars.component';
import { LandingResearchComponent } from 'app/core/landing/landing-research.component';
import { LandingSocialProofComponent } from 'app/core/landing/landing-social-proof.component';
import { LandingTrustComponent } from 'app/core/landing/landing-trust.component';
import { COMMUNITY_LINKS, FOOTER_LINK_GROUPS, PILLARS, RESEARCH_STEPS, TRUST_LINKS, UNIVERSITY_LOGOS } from 'app/core/landing/landing-data';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';

type LandingSection =
    | LandingPillarsComponent
    | LandingTrustComponent
    | LandingResearchComponent
    | LandingCommunityComponent
    | LandingHeroComponent
    | LandingSocialProofComponent
    | LandingFooterComponent;

/**
 * Renders one of the landing sections with the translate pipe stubbed out, so the assertions can
 * match on the raw translation keys. A router is provided because the hero and the footer navigate.
 */
async function render<T extends LandingSection>(component: new () => T): Promise<ComponentFixture<T>> {
    await TestBed.configureTestingModule({ imports: [component], providers: [provideRouter([])] })
        .overrideComponent(component, {
            remove: { imports: [ArtemisTranslatePipe] },
            add: { imports: [TranslatePipeMock] },
        })
        .compileComponents();

    const fixture = TestBed.createComponent(component);
    fixture.detectChanges();
    return fixture;
}

/**
 * Every landing link that leaves the application must open safely in a new tab, and must say so to
 * assistive technology. The visually hidden hint is easy to forget on a new call to action, so it is
 * asserted here for every outbound link rather than spot-checked.
 */
function expectSafeExternalLinks(anchors: HTMLAnchorElement[]): void {
    expect(anchors.length).toBeGreaterThan(0);
    for (const anchor of anchors) {
        expect(anchor.getAttribute('href')).toMatch(/^https:\/\//);
        expect(anchor.getAttribute('target')).toBe('_blank');
        expect(anchor.getAttribute('rel')).toContain('noopener');
        expect(anchor.querySelector('.visually-hidden')?.textContent).toContain('landing.opensInNewTab');
    }
}

describe('Landing informational sections', () => {
    beforeEach(() => {
        TestBed.resetTestingModule();
    });

    describe('LandingHeroComponent', () => {
        it('should render the tagline, headline, and subtitle', async () => {
            const fixture = await render(LandingHeroComponent);

            expect(fixture.nativeElement.querySelector('.tag').textContent).toContain('landing.hero.tagline');
            expect(fixture.nativeElement.querySelector('h1.hero-title').textContent).toContain('landing.hero.title');
            expect(fixture.nativeElement.querySelector('.hero-subtitle').textContent).toContain('landing.hero.subtitle');
        });

        it('should offer one primary action plus outbound links that open safely', async () => {
            const fixture = await render(LandingHeroComponent);

            expect(fixture.nativeElement.querySelectorAll('button.hero-action-primary')).toHaveLength(1);
            expectSafeExternalLinks(Array.from(fixture.nativeElement.querySelectorAll('a.hero-action')));
        });

        it('should navigate to the sign-in page when the primary action is used', async () => {
            const fixture = await render(LandingHeroComponent);
            const router = TestBed.inject(Router);
            const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

            fixture.nativeElement.querySelector('button.hero-action-primary').click();

            expect(navigate).toHaveBeenCalledExactlyOnceWith('/sign-in');
        });
    });

    describe('LandingSocialProofComponent', () => {
        it('should render every logo twice so the marquee can loop seamlessly', async () => {
            const fixture = await render(LandingSocialProofComponent);

            expect(fixture.nativeElement.querySelectorAll('img.logo-item')).toHaveLength(UNIVERSITY_LOGOS.length * 2);
        });

        it('should hide the duplicated logos from assistive technology', async () => {
            const fixture = await render(LandingSocialProofComponent);

            const logos: HTMLImageElement[] = Array.from(fixture.nativeElement.querySelectorAll('img.logo-item'));
            const duplicates = logos.slice(UNIVERSITY_LOGOS.length);
            expect(duplicates.every((logo) => logo.getAttribute('aria-hidden') === 'true')).toBe(true);
            expect(logos.slice(0, UNIVERSITY_LOGOS.length).every((logo) => !!logo.getAttribute('alt'))).toBe(true);
        });

        it('should link to the canonical adoption page', async () => {
            const fixture = await render(LandingSocialProofComponent);

            const cta: HTMLAnchorElement = fixture.nativeElement.querySelector('a.social-proof-link');
            expect(cta.getAttribute('href')).toBe('https://docs.artemis.tum.de/about/adoption');
            expectSafeExternalLinks([cta]);
        });
    });

    describe('LandingFooterComponent', () => {
        it('should render one column per link group', async () => {
            const fixture = await render(LandingFooterComponent);

            expect(fixture.nativeElement.querySelectorAll('.link-group')).toHaveLength(FOOTER_LINK_GROUPS.length);
        });

        it('should expose the project pages, including the trial and the roadmap', async () => {
            const fixture = await render(LandingFooterComponent);

            const hrefs = Array.from<HTMLAnchorElement>(fixture.nativeElement.querySelectorAll('a.link-item')).map((link) => link.getAttribute('href'));
            expect(hrefs).toContain('https://docs.artemis.tum.de/about');
            expect(hrefs).toContain('https://docs.artemis.tum.de/about/try');
            expect(hrefs).toContain('https://docs.artemis.tum.de/about/roadmap');
            expect(hrefs).toContain('https://github.com/ls1intum/Artemis');
        });

        it('should navigate to the sign-in page from the footer call to action', async () => {
            const fixture = await render(LandingFooterComponent);
            const router = TestBed.inject(Router);
            const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

            fixture.nativeElement.querySelector('button.cta-btn').click();

            expect(navigate).toHaveBeenCalledExactlyOnceWith('/sign-in');
        });
    });

    describe('LandingPillarsComponent', () => {
        it('should render one heading per pillar', async () => {
            const fixture = await render(LandingPillarsComponent);

            const titles = fixture.nativeElement.querySelectorAll('.pillar-title');
            expect(titles).toHaveLength(PILLARS.length);
            expect(titles[0].textContent).toContain(PILLARS[0].titleKey);
        });

        it('should label the section by its heading', async () => {
            const fixture = await render(LandingPillarsComponent);

            const section = fixture.nativeElement.querySelector('section');
            expect(section.getAttribute('aria-labelledby')).toBe('pillars-title');
            expect(fixture.nativeElement.querySelector('#pillars-title')).not.toBeNull();
        });
    });

    describe('LandingTrustComponent', () => {
        it('should render one card per trust topic', async () => {
            const fixture = await render(LandingTrustComponent);

            const cards = fixture.nativeElement.querySelectorAll('a.trust-card');
            expect(cards).toHaveLength(TRUST_LINKS.length);
        });

        it('should open every card in a new tab without leaking the referrer window', async () => {
            const fixture = await render(LandingTrustComponent);

            expectSafeExternalLinks(Array.from(fixture.nativeElement.querySelectorAll('a.trust-card')));
        });
    });

    describe('LandingResearchComponent', () => {
        it('should render the research loop as an ordered list', async () => {
            const fixture = await render(LandingResearchComponent);

            const steps = fixture.nativeElement.querySelectorAll('ol.research-steps > li');
            expect(steps).toHaveLength(RESEARCH_STEPS.length);
        });

        it('should link to the publications page', async () => {
            const fixture = await render(LandingResearchComponent);

            const cta: HTMLAnchorElement = fixture.nativeElement.querySelector('a.research-cta');
            expect(cta.getAttribute('href')).toBe('https://docs.artemis.tum.de/publications');
        });
    });

    describe('LandingCommunityComponent', () => {
        it('should render one card per community link', async () => {
            const fixture = await render(LandingCommunityComponent);

            const cards = fixture.nativeElement.querySelectorAll('a.community-card');
            expect(cards).toHaveLength(COMMUNITY_LINKS.length);
        });

        it('should open every card in a new tab and announce that', async () => {
            const fixture = await render(LandingCommunityComponent);

            expectSafeExternalLinks(Array.from(fixture.nativeElement.querySelectorAll('a.community-card')));
        });

        it('should link to the repository and the contribution guide', async () => {
            const fixture = await render(LandingCommunityComponent);

            const hrefs = Array.from<HTMLAnchorElement>(fixture.nativeElement.querySelectorAll('a.community-card')).map((card) => card.getAttribute('href'));
            expect(hrefs).toContain('https://github.com/ls1intum/Artemis');
            expect(hrefs).toContain('https://github.com/ls1intum/Artemis/blob/develop/CONTRIBUTING.md');
        });
    });
});
