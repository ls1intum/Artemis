import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockDirective, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import {
    MODULE_FEATURE_ATHENA,
    MODULE_FEATURE_ATLAS,
    MODULE_FEATURE_EXAM,
    MODULE_FEATURE_IRIS,
    MODULE_FEATURE_LDAP,
    MODULE_FEATURE_LECTURE,
    MODULE_FEATURE_PASSKEY_REQUIRE_ADMIN,
    MODULE_FEATURE_TEXT,
} from 'app/app.constants';
import { AboutUsComponent } from 'app/core/about-us/about-us.component';
import { HIGHLIGHTS, MAX_HIGHLIGHTS, MODULES } from 'app/core/about-us/about-us-data';
import { AboutUsModel } from 'app/core/about-us/models/about-us-model';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { StaticContentService } from 'app/foundation/service/static-content.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import enAboutUs from 'src/main/webapp/i18n/en/aboutUs.json';
import deAboutUs from 'src/main/webapp/i18n/de/aboutUs.json';
import enFeatureUsage from 'src/main/webapp/i18n/en/featureUsage.json';
import deFeatureUsage from 'src/main/webapp/i18n/de/featureUsage.json';

describe('AboutUsComponent', () => {
    let fixture: ComponentFixture<AboutUsComponent>;
    let getStaticJson: ReturnType<typeof vi.spyOn>;

    const maintainers: AboutUsModel = {
        projectManagers: [
            { fullName: 'Erika Muster', photoDirectory: 'public/images/about/Erika_Muster.jpg', role: 'projectManager', website: 'https://example.org/erika' },
            { fullName: 'Max Beispiel', photoDirectory: 'public/images/about/Max_Beispiel.jpg' },
        ],
    };

    function profile(overrides: Partial<ProfileInfo> = {}): ProfileInfo {
        const info = new ProfileInfo();
        info.universityName = 'Technical University of Munich';
        info.operatorName = 'Research group of Applied Education Technologies';
        info.operatorAdminName = 'Erika Muster';
        info.contact = 'artemis@example.org';
        info.activeModuleFeatures = [];
        info.testServer = false;
        info.git = { branch: 'feature/about', commit: { id: { abbrev: 'abc1234' } } } as ProfileInfo['git'];
        Object.entries(overrides).forEach(([key, value]) => ((info as unknown as Record<string, unknown>)[key] = value));
        return info;
    }

    function render(info: ProfileInfo) {
        vi.spyOn(TestBed.inject(ProfileService), 'getProfileInfo').mockReturnValue(info);
        fixture.detectChanges();
    }

    function byTestId(testId: string): HTMLElement | null {
        return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
    }

    function testIdsStartingWith(prefix: string): string[] {
        return Array.from<HTMLElement>(fixture.nativeElement.querySelectorAll(`[data-testid^="${prefix}"]`)).map((element) => element.dataset['testid']!.substring(prefix.length));
    }

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [AboutUsComponent, MockDirective(TranslateDirective)],
            providers: [
                { provide: ActivatedRoute, useValue: { snapshot: { url: ['about'] } } },
                MockProvider(ProfileService),
                MockProvider(StaticContentService),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();
        getStaticJson = vi.spyOn(TestBed.inject(StaticContentService), 'getStaticJsonFromArtemisServer').mockReturnValue(of(maintainers));
        fixture = TestBed.createComponent(AboutUsComponent);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('describes the installation from its configuration', () => {
        render(profile());

        expect(byTestId('about-university')?.textContent).toContain('Technical University of Munich');
        expect(byTestId('about-operator')?.textContent).toContain('Research group of Applied Education Technologies');
        expect(byTestId('about-admin')?.textContent).toContain('Erika Muster');
        expect(byTestId('about-contact')?.getAttribute('href')).toMatch(/^mailto:artemis@example\.org\?body=/);
        expect(byTestId('about-contact-tile')).not.toBeNull();
        expect(byTestId('about-test-server')).toBeNull();
    });

    it('marks a test server and shows the branch it runs', () => {
        render(profile({ testServer: true }));

        expect(byTestId('about-test-server')).not.toBeNull();
        expect(byTestId('about-branch')?.textContent).toContain('feature/about');
        expect(byTestId('about-commit')?.textContent).toContain('abc1234');
    });

    it('hides the branch on a production installation, whose version already names the release', () => {
        render(profile());

        expect(byTestId('about-branch')).toBeNull();
        expect(byTestId('about-commit')?.textContent).toContain('abc1234');
    });

    it('omits the contact entries when no contact address is configured', () => {
        render(profile({ contact: '' }));

        expect(byTestId('about-contact')).toBeNull();
        expect(byTestId('about-contact-tile')).toBeNull();
    });

    it('names the operator in the project section', () => {
        render(profile());

        expect(byTestId('about-operated-by')).not.toBeNull();
    });

    it('omits installation metadata that a development server leaves out', () => {
        render(profile({ universityName: undefined, operatorName: '', operatorAdminName: undefined, contact: undefined }));

        expect(byTestId('about-installation')).toBeNull();
        expect(byTestId('about-operated-by')).toBeNull();
    });

    it('shows only the installation metadata that is set', () => {
        render(profile({ universityName: undefined, operatorAdminName: undefined, contact: undefined }));

        expect(byTestId('about-installation')).not.toBeNull();
        expect(byTestId('about-university')).toBeNull();
        expect(byTestId('about-operator')?.textContent).toContain('Research group of Applied Education Technologies');
        expect(byTestId('about-admin')).toBeNull();
    });

    it('keeps the test server tag without installation metadata', () => {
        render(profile({ universityName: undefined, operatorName: undefined, operatorAdminName: undefined, contact: undefined, testServer: true }));

        expect(byTestId('about-test-server')).not.toBeNull();
        expect(byTestId('about-university')).toBeNull();
    });

    it('lists enabled user-facing modules in display order and ignores infrastructure flags', () => {
        render(profile({ activeModuleFeatures: [MODULE_FEATURE_IRIS, MODULE_FEATURE_LDAP, MODULE_FEATURE_EXAM, MODULE_FEATURE_PASSKEY_REQUIRE_ADMIN, MODULE_FEATURE_TEXT] }));

        expect(testIdsStartingWith('about-module-')).toEqual([MODULE_FEATURE_EXAM, MODULE_FEATURE_TEXT, MODULE_FEATURE_IRIS]);
    });

    it('omits the modules section when no optional module is enabled', () => {
        render(profile());

        expect(byTestId('about-modules')).toBeNull();
    });

    it('links the exam feature pages only where the exam mode is enabled', () => {
        render(profile({ activeModuleFeatures: [MODULE_FEATURE_EXAM] }));

        expect(byTestId('about-exam-features-students')?.getAttribute('href')).toBe('/features/students');
        expect(byTestId('about-exam-features-instructors')?.getAttribute('href')).toBe('/features/instructors');
    });

    it('does not link the exam feature pages without the exam mode', () => {
        render(profile());

        expect(byTestId('about-exam-features')).toBeNull();
    });

    it('highlights only features whose module is enabled', () => {
        render(profile({ activeModuleFeatures: [MODULE_FEATURE_IRIS] }));

        expect(testIdsStartingWith('about-highlight-')).toEqual(['PROGRAMMING_ONLINE_EDITOR', 'PROGRAMMING_RESULTS', 'IRIS_CHAT', 'QUIZ_LIVE', 'MESSAGING']);
    });

    it(`highlights at most ${MAX_HIGHLIGHTS} features`, () => {
        render(profile({ activeModuleFeatures: [MODULE_FEATURE_EXAM, MODULE_FEATURE_IRIS, MODULE_FEATURE_ATLAS, MODULE_FEATURE_ATHENA, MODULE_FEATURE_LECTURE] }));

        expect(testIdsStartingWith('about-highlight-')).toEqual(['PROGRAMMING_ONLINE_EDITOR', 'PROGRAMMING_RESULTS', 'EXAM_TAKE', 'IRIS_CHAT', 'QUIZ_LIVE', 'LEARNING_PATHS']);
    });

    it('shows the maintainers from about-us.json with small avatars and optional links', () => {
        render(profile());

        expect(getStaticJson).toHaveBeenCalledExactlyOnceWith('about-us.json');
        const avatars = fixture.debugElement.queryAll(By.css('[data-testid="about-maintainers"] img'));
        expect(avatars.map((avatar) => avatar.attributes['src'])).toEqual(['public/images/about/Erika_Muster.jpg', 'public/images/about/Max_Beispiel.jpg']);
        expect(avatars.every((avatar) => avatar.attributes['width'] === '48')).toBe(true);
        const link: HTMLAnchorElement = fixture.nativeElement.querySelector('[data-testid="about-maintainers"] a[href="https://example.org/erika"]');
        expect(link.textContent).toContain('Erika Muster');
        expect(byTestId('about-contributors')?.getAttribute('href')).toBe('https://github.com/ls1intum/Artemis/graphs/contributors');
    });

    it('tolerates an overridden about-us.json without maintainers', () => {
        getStaticJson.mockReturnValue(of({}));
        render(profile());

        expect(byTestId('about-maintainers')).toBeNull();
    });

    it('confirms a copied citation', () => {
        render(profile());
        const copyButton = fixture.debugElement.query(By.css('[data-testid="about-citation-copy"]'));

        expect(copyButton.nativeElement.dataset['copied']).toBe('false');

        copyButton.triggerEventHandler('cdkCopyToClipboardCopied', true);
        fixture.detectChanges();

        expect(copyButton.nativeElement.dataset['copied']).toBe('true');
    });

    describe('translations', () => {
        const catalogues = { en: enFeatureUsage.artemisApp.featureUsage.catalog.feature, de: deFeatureUsage.artemisApp.featureUsage.catalog.feature };
        const modules = { en: enAboutUs.artemisApp.aboutUsOverview.modules, de: deAboutUs.artemisApp.aboutUsOverview.modules };

        it.each(['en', 'de'] as const)('names and describes every highlight through the feature catalogue (%s)', (language) => {
            for (const highlight of HIGHLIGHTS) {
                const entry = (catalogues[language] as Record<string, { name?: string; description?: string }>)[highlight.catalogueKey];
                expect(entry?.name, highlight.catalogueKey).toBeTruthy();
                expect(entry?.description, highlight.catalogueKey).toBeTruthy();
            }
        });

        it.each(['en', 'de'] as const)('names every listed module (%s)', (language) => {
            for (const module of MODULES) {
                expect((modules[language] as Record<string, string>)[module.translationKey], module.translationKey).toBeTruthy();
            }
        });
    });
});
