import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { FeatureOverviewComponent, TargetAudience } from 'app/core/feature-overview/feature-overview.component';
import { By } from '@angular/platform-browser';
import { DebugElement } from '@angular/core';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('Feature Overview Component', () => {
    setupTestBed({ zoneless: true });

    let comp: FeatureOverviewComponent;
    let fixture: ComponentFixture<FeatureOverviewComponent>;
    let debugElement: DebugElement;

    describe('Target Audience: Instructors', () => {
        const route = { snapshot: { url: ['instructors'] } } as any as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                providers: [
                    { provide: ActivatedRoute, useValue: route },
                    { provide: ProfileService, useValue: MockProfileService },
                    { provide: TranslateService, useClass: MockTranslateService },
                ],
            }).compileComponents();

            fixture = TestBed.createComponent(FeatureOverviewComponent);
            debugElement = fixture.debugElement;
            comp = fixture.componentInstance;
        });

        afterEach(() => {
            vi.restoreAllMocks();
        });

        describe('onInit', () => {
            it('should load all features for instructors', () => {
                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.targetAudience).toEqual(TargetAudience.INSTRUCTORS);
                expect(comp.features.length).toBeGreaterThan(0);
            });

            it('should ensure all features have unique IDs', () => {
                // WHEN
                comp.ngOnInit();

                // THEN
                for (const featureA of comp.features) {
                    for (const featureB of comp.features) {
                        if (featureA !== featureB) {
                            expect(featureA.id === featureB.id).toBe(false);
                        }
                    }
                }
            });
        });

        describe('Navigate to Feature Details', () => {
            it('should scroll to the correct feature detail', async () => {
                const navigateToFeatureSpy = vi.spyOn(comp, 'navigateToFeature');
                // WHEN
                comp.ngOnInit();
                fixture.detectChanges();
                await fixture.whenStable();
                const id = '#featureOverview' + comp.features[0].id;
                const featureOverview = debugElement.query(By.css(id));

                featureOverview.nativeElement.click();

                // THEN
                expect(navigateToFeatureSpy).toHaveBeenCalledWith(comp.features[0].id);
            });
        });
    });
});
