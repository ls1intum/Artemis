/**
 * Vitest tests for AdminFeatureToggleComponent.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TranslateService } from '@ngx-translate/core';

import { AdminFeatureToggleComponent } from 'app/core/admin/features/admin-feature-toggle.component';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockFeatureToggleService } from 'test/helpers/mocks/service/mock-feature-toggle.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MODULE_FEATURE_ATLAS, MODULE_FEATURE_EXAM, PROFILE_ATHENA, PROFILE_IRIS } from 'app/app.constants';

describe('AdminFeatureToggleComponentTest', () => {
    setupTestBed({ zoneless: true });

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
            expect(comp.featureToggles()).toHaveLength(12);
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

        it('should set documentation links for features that have them', () => {
            comp.ngOnInit();
            const toggles = comp.featureToggles();

            const programmingExercise = toggles.find((t) => t.feature === FeatureToggle.ProgrammingExercises);
            expect(programmingExercise?.documentationLink).toBeDefined();
            expect(programmingExercise?.documentationLink).toContain('ls1intum.github.io/Artemis');

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
            expect(comp.profileFeatures()).toHaveLength(10);
        });

        it('should set isActive based on active profiles', () => {
            // Mock profile service to return some active profiles
            vi.spyOn(mockProfileService, 'isProfileActive').mockImplementation((profile: string) => {
                return profile === PROFILE_IRIS;
            });

            comp.ngOnInit();
            const profiles = comp.profileFeatures();

            const iris = profiles.find((p) => p.profile === PROFILE_IRIS);
            expect(iris?.isActive).toBe(true);

            const athena = profiles.find((p) => p.profile === PROFILE_ATHENA);
            expect(athena?.isActive).toBe(false);
        });

        it('should set documentation links for profile features', () => {
            comp.ngOnInit();
            const profiles = comp.profileFeatures();

            const iris = profiles.find((p) => p.profile === PROFILE_IRIS);
            expect(iris?.documentationLink).toBeDefined();
            expect(iris?.documentationLink).toContain('ls1intum.github.io/Artemis');
        });
    });

    describe('Module Features', () => {
        it('ngOnInit should load module features', () => {
            expect(comp.moduleFeatures()).toHaveLength(0);
            comp.ngOnInit();
            expect(comp.moduleFeatures()).toHaveLength(13);
        });

        it('should set isActive based on active module features', () => {
            // Mock profile service to return some active module features
            vi.spyOn(mockProfileService, 'isModuleFeatureActive').mockImplementation((feature: string) => {
                return feature === MODULE_FEATURE_ATLAS;
            });

            comp.ngOnInit();
            const modules = comp.moduleFeatures();

            const atlas = modules.find((m) => m.feature === MODULE_FEATURE_ATLAS);
            expect(atlas?.isActive).toBe(true);

            const exam = modules.find((m) => m.feature === MODULE_FEATURE_EXAM);
            expect(exam?.isActive).toBe(false);
        });

        it('should set documentation links for module features that have them', () => {
            comp.ngOnInit();
            const modules = comp.moduleFeatures();

            const atlas = modules.find((m) => m.feature === MODULE_FEATURE_ATLAS);
            expect(atlas?.documentationLink).toBeDefined();
            expect(atlas?.documentationLink).toContain('ls1intum.github.io/Artemis');
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
            const key = comp.getProfileNameKey(PROFILE_IRIS);
            expect(key).toBe('artemisApp.features.profiles.iris.name');
        });

        it('getProfileDescriptionKey should return correct translation key', () => {
            const key = comp.getProfileDescriptionKey(PROFILE_ATHENA);
            expect(key).toBe('artemisApp.features.profiles.athena.description');
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
});
