import { afterEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockDirective, MockProvider } from 'ng-mocks';
import { FeatureOverviewComponent } from 'app/core/feature-overview/feature-overview.component';
import { INSTRUCTOR_FEATURES, STUDENT_FEATURES, TargetAudience } from 'app/core/feature-overview/feature-overview-data';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import enFeatureOverview from 'src/main/webapp/i18n/en/featureOverview.json';
import deFeatureOverview from 'src/main/webapp/i18n/de/featureOverview.json';

describe('FeatureOverviewComponent', () => {
    let fixture: ComponentFixture<FeatureOverviewComponent>;

    async function render(path: 'students' | 'instructors', accountName = 'Artemis') {
        await TestBed.configureTestingModule({
            imports: [FeatureOverviewComponent, MockDirective(TranslateDirective)],
            providers: [
                provideRouter([]),
                { provide: ActivatedRoute, useValue: { snapshot: { url: [path] } } },
                MockProvider(ProfileService, { getProfileInfo: () => ({ accountName }) as ProfileInfo }),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(FeatureOverviewComponent);
        fixture.detectChanges();
    }

    function testIdsStartingWith(prefix: string): string[] {
        return Array.from<HTMLElement>(fixture.nativeElement.querySelectorAll(`[data-testid^="${prefix}"]`)).map((element) => element.dataset['testid']!.substring(prefix.length));
    }

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('shows the instructor features with an overview card and a detail section each', async () => {
        await render('instructors');

        const keys = INSTRUCTOR_FEATURES.map((feature) => feature.key);
        expect(fixture.componentInstance.targetAudience()).toBe(TargetAudience.INSTRUCTORS);
        expect(testIdsStartingWith('feature-card-')).toEqual(keys);
        expect(testIdsStartingWith('feature-detail-')).toEqual(keys);
    });

    it('shows the student features without the TUM login elsewhere', async () => {
        await render('students');

        expect(fixture.componentInstance.targetAudience()).toBe(TargetAudience.STUDENTS);
        expect(testIdsStartingWith('feature-card-')).toEqual(STUDENT_FEATURES.filter((feature) => !feature.tumOnly).map((feature) => feature.key));
    });

    it('adds the TUM login where users sign in with a TUM account', async () => {
        await render('students', 'TUM');

        expect(testIdsStartingWith('feature-card-')).toContain('login');
    });

    it('marks the current audience in the switch', async () => {
        await render('students');

        const students: HTMLElement = fixture.nativeElement.querySelector('[data-testid="feature-overview-students"]');
        const instructors: HTMLElement = fixture.nativeElement.querySelector('[data-testid="feature-overview-instructors"]');
        expect(students.getAttribute('aria-current')).toBe('page');
        expect(instructors.hasAttribute('aria-current')).toBe(false);
        expect(instructors.getAttribute('href')).toBe('/features/instructors');
    });

    it('scrolls to the details of a clicked feature', async () => {
        await render('instructors');
        const detail: HTMLElement = fixture.nativeElement.querySelector('[data-testid="feature-detail-checklist"]');
        detail.scrollIntoView = vi.fn();

        fixture.nativeElement.querySelector('[data-testid="feature-card-checklist"]').click();

        expect(detail.scrollIntoView).toHaveBeenCalledExactlyOnceWith({ behavior: 'smooth', block: 'start' });
    });

    describe.each([
        ['en', enFeatureOverview.featureOverview],
        ['de', deFeatureOverview.featureOverview],
    ])('translations (%s)', (_, translations) => {
        it.each([
            [TargetAudience.STUDENTS, STUDENT_FEATURES],
            [TargetAudience.INSTRUCTORS, INSTRUCTOR_FEATURES],
        ] as const)('names and describes every %s feature', (audience, features) => {
            const catalogue = translations[audience].feature as Record<string, { title?: string; shortDescription?: string; descriptionTextOne?: string }>;
            for (const feature of features) {
                expect(catalogue[feature.key]?.title, feature.key).toBeTruthy();
                expect(catalogue[feature.key]?.shortDescription, feature.key).toBeTruthy();
                expect(catalogue[feature.key]?.descriptionTextOne, feature.key).toBeTruthy();
            }
        });
    });
});
