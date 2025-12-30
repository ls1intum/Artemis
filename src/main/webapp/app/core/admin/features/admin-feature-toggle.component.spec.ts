/**
 * Vitest tests for AdminFeatureToggleComponent.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TranslateService } from '@ngx-translate/core';

import { AdminFeatureToggleComponent } from 'app/core/admin/features/admin-feature-toggle.component';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { MockFeatureToggleService } from 'test/helpers/mocks/service/mock-feature-toggle.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('AdminFeatureToggleComponentTest', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<AdminFeatureToggleComponent>;
    let comp: AdminFeatureToggleComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [AdminFeatureToggleComponent],
            providers: [
                { provide: FeatureToggleService, useClass: MockFeatureToggleService },
                { provide: TranslateService, useClass: MockTranslateService },
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

    it('getFeatureNameKey should return correct translation key', () => {
        const key = comp.getFeatureNameKey(FeatureToggle.ProgrammingExercises);
        expect(key).toBe('artemisApp.featureToggle.features.ProgrammingExercises.name');
    });

    it('getFeatureDescriptionKey should return correct translation key', () => {
        const key = comp.getFeatureDescriptionKey(FeatureToggle.PlagiarismChecks);
        expect(key).toBe('artemisApp.featureToggle.features.PlagiarismChecks.description');
    });

    it('getFeatureWarningKey should return correct translation key', () => {
        const key = comp.getFeatureWarningKey(FeatureToggle.LearningPaths);
        expect(key).toBe('artemisApp.featureToggle.features.LearningPaths.disableWarning');
    });

    it('should set documentation links for features that have them', () => {
        comp.ngOnInit();
        const toggles = comp.featureToggles();

        const programmingExercise = toggles.find((t) => t.feature === FeatureToggle.ProgrammingExercises);
        expect(programmingExercise?.documentationLink).toBeDefined();
        expect(programmingExercise?.documentationLink).toContain('docs.artemis.cit.tum.de');

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
