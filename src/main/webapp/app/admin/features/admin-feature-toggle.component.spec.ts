/**
 * Vitest tests for AdminFeatureToggleComponent.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, of, throwError } from 'rxjs';

import { AdminFeatureToggleComponent } from 'app/admin/features/admin-feature-toggle.component';
import { FeatureToggle, FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockFeatureToggleService } from 'test/helpers/mocks/service/mock-feature-toggle.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MODULE_FEATURE_ATHENA, MODULE_FEATURE_ATLAS, MODULE_FEATURE_EXAM, MODULE_FEATURE_IRIS, MODULE_FEATURE_PASSKEY_REQUIRE_ADMIN, PROFILE_JENKINS } from 'app/app.constants';

describe('AdminFeatureToggleComponentTest', () => {
    let fixture: ComponentFixture<AdminFeatureToggleComponent>;
    let comp: AdminFeatureToggleComponent;
    let mockProfileService: MockProfileService;

    beforeEach(async () => {
        mockProfileService = new MockProfileService();

        await TestBed.configureTestingModule({
            imports: [AdminFeatureToggleComponent],
            providers: [
                { provide: FeatureToggleService, useClass: MockFeatureToggleService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useValue: mockProfileService },
            ],
        })
            .overrideTemplate(AdminFeatureToggleComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(AdminFeatureToggleComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('Feature Toggles', () => {
        it('constructor should not load toggles', () => {
            expect(comp.featureToggles()).toHaveLength(0);
        });

        it('ngOnInit should load all feature toggles', () => {
            expect(comp.featureToggles()).toHaveLength(0);
            comp.ngOnInit();
            expect(comp.featureToggles()).toHaveLength(15);
        });

        it('ngOnInit should set isActive based on active toggles', () => {
            comp.ngOnInit();
            const toggles = comp.featureToggles();
            // All features should be active by default in the mock service
            expect(toggles.every((toggle) => toggle.isActive)).toBe(true);
        });

        it('onFeatureToggle should toggle feature off when active', () => {
            comp.ngOnInit();
            const featureInfo = comp.featureToggles()[0];
            expect(featureInfo.isActive).toBe(true);

            comp.onFeatureToggle(featureInfo);

            expect(comp.featureToggles()[0].isActive).toBe(false);
        });

        it('onFeatureToggle should toggle feature on when inactive', () => {
            comp.ngOnInit();
            // First toggle off
            const featureInfo = comp.featureToggles()[0];
            comp.onFeatureToggle(featureInfo);
            expect(comp.featureToggles()[0].isActive).toBe(false);

            // Then toggle on
            comp.onFeatureToggle(comp.featureToggles()[0]);
            expect(comp.featureToggles()[0].isActive).toBe(true);
        });

        it('onFeatureToggle should optimistically flip, then revert and alert when the update fails', () => {
            comp.ngOnInit();
            const featureToggleService = TestBed.inject(FeatureToggleService);
            // A controllable source so we can observe the flip BEFORE the server responds — otherwise a regression
            // that drops the optimistic flip (reintroducing the UI-lie bug) would still pass on the final state alone.
            const response = new Subject<object>();
            vi.spyOn(featureToggleService, 'setFeatureToggleState').mockReturnValue(response);
            const errorSpy = vi.spyOn(TestBed.inject(AlertService), 'error');

            const featureInfo = comp.featureToggles()[0];
            expect(featureInfo.isActive).toBe(true);

            comp.onFeatureToggle(featureInfo);
            // Optimistic flip is applied immediately, while the request is still in flight.
            expect(comp.featureToggles()[0].isActive).toBe(false);

            // Server rejects the change: the flip rolls back to the true state and the error surfaces.
            response.error(new HttpErrorResponse({ status: 400 }));
            expect(comp.featureToggles()[0].isActive).toBe(true);
            expect(errorSpy).toHaveBeenCalledOnce();
        });

        it('onFeatureToggle should serialize updates per feature, ignoring clicks while a request is in flight', () => {
            comp.ngOnInit();
            const featureToggleService = TestBed.inject(FeatureToggleService);
            const inFlight = new Subject<object>();
            const spy = vi.spyOn(featureToggleService, 'setFeatureToggleState').mockReturnValueOnce(inFlight).mockReturnValueOnce(of({}));

            const feature = comp.featureToggles()[0].feature;
            expect(comp.featureToggles()[0].isActive).toBe(true);

            // First click: optimistic off, one request in flight, feature marked pending (its switch is disabled).
            comp.onFeatureToggle(comp.featureToggles()[0]);
            expect(comp.featureToggles()[0].isActive).toBe(false);
            expect(comp.pendingFeatures().has(feature)).toBe(true);
            expect(spy).toHaveBeenCalledTimes(1);

            // A click while the request is in flight is ignored: no second request, state unchanged. Serializing
            // the writes prevents an older successful request from overwriting a newer server state.
            comp.onFeatureToggle(comp.featureToggles()[0]);
            expect(spy).toHaveBeenCalledTimes(1);
            expect(comp.featureToggles()[0].isActive).toBe(false);

            // Once the request completes, the feature is interactive again and the next click is sent in order.
            inFlight.next({});
            inFlight.complete();
            expect(comp.pendingFeatures().has(feature)).toBe(false);
            comp.onFeatureToggle(comp.featureToggles()[0]);
            expect(spy).toHaveBeenCalledTimes(2);
            expect(comp.featureToggles()[0].isActive).toBe(true);
        });

        it('should alert and leave toggles empty when loading feature toggles fails', () => {
            const featureToggleService = TestBed.inject(FeatureToggleService);
            vi.spyOn(featureToggleService, 'getFeatureToggles').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));
            const errorSpy = vi.spyOn(TestBed.inject(AlertService), 'error');

            comp.ngOnInit();

            expect(errorSpy).toHaveBeenCalledOnce();
            expect(comp.featureToggles()).toHaveLength(0);
            // Profile and module features load independently and must still be populated despite the toggle failure.
            expect(comp.profileFeatures()).toHaveLength(3);
            expect(comp.moduleFeatures()).toHaveLength(20);
        });

        it('should set documentation links for features that have them', () => {
            comp.ngOnInit();
            const toggles = comp.featureToggles();

            const programmingExercise = toggles.find((t) => t.feature === FeatureToggle.ProgrammingExercises);
            expect(programmingExercise?.documentationLink).toBeDefined();
            expect(programmingExercise?.documentationLink).toContain('docs.artemis.tum.de');

            const plagiarismChecks = toggles.find((t) => t.feature === FeatureToggle.PlagiarismChecks);
            expect(plagiarismChecks?.documentationLink).toBeDefined();
        });

        it('should not set documentation links for features without them', () => {
            comp.ngOnInit();
            const toggles = comp.featureToggles();

            const science = toggles.find((t) => t.feature === FeatureToggle.Science);
            expect(science?.documentationLink).toBeUndefined();
        });
    });

    describe('Profile Features', () => {
        it('ngOnInit should load profile features', () => {
            expect(comp.profileFeatures()).toHaveLength(0);
            comp.ngOnInit();
            expect(comp.profileFeatures()).toHaveLength(3);
        });

        it('should set isActive based on active profiles', () => {
            // Mock profile service to return some active profiles
            vi.spyOn(mockProfileService, 'isProfileActive').mockImplementation((profile: string) => {
                return profile === PROFILE_JENKINS;
            });

            comp.ngOnInit();
            const profiles = comp.profileFeatures();

            const jenkins = profiles.find((p) => p.profile === PROFILE_JENKINS);
            expect(jenkins?.isActive).toBe(true);
        });

        it('should set documentation links for profile features', () => {
            comp.ngOnInit();
            const profiles = comp.profileFeatures();

            const jenkins = profiles.find((p) => p.profile === PROFILE_JENKINS);
            expect(jenkins?.documentationLink).toBeDefined();
            expect(jenkins?.documentationLink).toContain('docs.artemis.tum.de');
        });
    });

    describe('Module Features', () => {
        it('ngOnInit should load module features', () => {
            expect(comp.moduleFeatures()).toHaveLength(0);
            comp.ngOnInit();
            expect(comp.moduleFeatures()).toHaveLength(20);
        });

        it('should set isActive based on active module features', () => {
            // Mock profile service to return some active module features
            vi.spyOn(mockProfileService, 'isModuleFeatureActive').mockImplementation((feature: string) => {
                return feature === MODULE_FEATURE_IRIS;
            });

            comp.ngOnInit();
            const modules = comp.moduleFeatures();

            const iris = modules.find((m) => m.feature === MODULE_FEATURE_IRIS);
            expect(iris?.isActive).toBe(true);

            const atlas = modules.find((m) => m.feature === MODULE_FEATURE_ATLAS);
            expect(atlas?.isActive).toBe(false);
        });

        it('should set documentation links for module features that have them', () => {
            comp.ngOnInit();
            const modules = comp.moduleFeatures();

            const iris = modules.find((m) => m.feature === MODULE_FEATURE_IRIS);
            expect(iris?.documentationLink).toBeDefined();
            expect(iris?.documentationLink).toContain('docs.artemis.tum.de');
        });

        it('should include the passkey admin requirement module feature', () => {
            vi.spyOn(mockProfileService, 'isModuleFeatureActive').mockImplementation((feature: string) => {
                return feature === MODULE_FEATURE_PASSKEY_REQUIRE_ADMIN;
            });

            comp.ngOnInit();
            const modules = comp.moduleFeatures();

            const passkeyAdmin = modules.find((m) => m.feature === MODULE_FEATURE_PASSKEY_REQUIRE_ADMIN);
            expect(passkeyAdmin).toBeDefined();
            expect(passkeyAdmin?.isActive).toBe(true);
            expect(passkeyAdmin?.documentationLink).toContain('docs.artemis.tum.de');
        });
    });

    describe('Navigation', () => {
        it('scrollToSection should scroll the target element into view', () => {
            const scrollIntoView = vi.fn();
            vi.spyOn(document, 'getElementById').mockReturnValue({ scrollIntoView } as unknown as HTMLElement);

            comp.scrollToSection('module-features');

            expect(document.getElementById).toHaveBeenCalledWith('module-features');
            expect(scrollIntoView).toHaveBeenCalledWith({ behavior: 'smooth', block: 'start' });
        });

        it('scrollToSection should do nothing when the target element is missing', () => {
            vi.spyOn(document, 'getElementById').mockReturnValue(null);

            expect(() => comp.scrollToSection('missing-section')).not.toThrow();
        });
    });

    describe('Translation Keys', () => {
        it('getFeatureNameKey should return correct translation key', () => {
            const key = comp.getFeatureNameKey(FeatureToggle.ProgrammingExercises);
            expect(key).toBe('artemisApp.features.toggles.ProgrammingExercises.name');
        });

        it('getFeatureDescriptionKey should return correct translation key', () => {
            const key = comp.getFeatureDescriptionKey(FeatureToggle.PlagiarismChecks);
            expect(key).toBe('artemisApp.features.toggles.PlagiarismChecks.description');
        });

        it('getFeatureWarningKey should return correct translation key', () => {
            const key = comp.getFeatureWarningKey(FeatureToggle.LearningPaths);
            expect(key).toBe('artemisApp.features.toggles.LearningPaths.disableWarning');
        });

        it('getProfileNameKey should return correct translation key', () => {
            const key = comp.getProfileNameKey(PROFILE_JENKINS);
            expect(key).toBe('artemisApp.features.profiles.jenkins.name');
        });

        it('getProfileDescriptionKey should return correct translation key', () => {
            const key = comp.getProfileDescriptionKey(PROFILE_JENKINS);
            expect(key).toBe('artemisApp.features.profiles.jenkins.description');
        });

        it('getModuleFeatureNameKey should return correct translation key for Athena', () => {
            const key = comp.getModuleFeatureNameKey(MODULE_FEATURE_ATHENA);
            expect(key).toBe('artemisApp.features.modules.athena.name');
        });

        it('getModuleFeatureNameKey should return correct translation key', () => {
            const key = comp.getModuleFeatureNameKey(MODULE_FEATURE_ATLAS);
            expect(key).toBe('artemisApp.features.modules.atlas.name');
        });

        it('getModuleFeatureDescriptionKey should return correct translation key', () => {
            const key = comp.getModuleFeatureDescriptionKey(MODULE_FEATURE_EXAM);
            expect(key).toBe('artemisApp.features.modules.exam.description');
        });
    });

    describe('feature card colours', () => {
        // The two neutral surfaces used before were one shade apart and could not be told apart. The contrast now
        // comes from tinting the active state, while an inactive feature stays neutral: red is reserved for states
        // that need attention, so a switched-off feature must not read as a failure.
        it('should tint an active feature green and leave an inactive one neutral', () => {
            const active = comp['featureCardClasses'](true);
            const inactive = comp['featureCardClasses'](false);

            expect(active).toContain('bg-state-success');
            expect(active).toContain('border-state-success');
            expect(inactive).toContain('bg-surface-');
            expect(inactive).toContain('border-surface-');
            expect(active).not.toBe(inactive);
        });

        it('should not signal an inactive feature as an error', () => {
            const inactive = comp['featureCardClasses'](false);

            expect(inactive).not.toContain('danger');
            expect(inactive).not.toContain('warning');
        });

        it('should keep both branches readable in either theme', () => {
            // The state tokens resolve per theme on their own; the neutral surface shades need explicit `dark:` pairs.
            const active = comp['featureCardClasses'](true);
            const inactive = comp['featureCardClasses'](false);

            expect(active).not.toContain('dark:');
            expect(inactive).toContain('dark:bg-surface-');
            expect(inactive).toContain('dark:border-surface-');
            // Raw palette colours are never brand-bound and would break theming.
            for (const classes of [active, inactive]) {
                expect(classes).not.toMatch(/(bg|border)-(red|green|blue|gray)-\d/);
            }
        });
    });
});
