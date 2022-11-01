import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { ArtemisTestModule } from '../../test.module';
import { FeatureOverviewComponent, TargetAudience } from 'app/feature-overview/feature-overview.component';
import { By } from '@angular/platform-browser';
import { DebugElement } from '@angular/core';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { MockProfileService } from '../../helpers/mocks/service/mock-profile.service';

describe('Feature Overview Component', () => {
    let comp: FeatureOverviewComponent;
    let fixture: ComponentFixture<FeatureOverviewComponent>;
    let debugElement: DebugElement;

    describe('Target Audience: Instructors', () => {
        const route = { snapshot: { url: ['instructors'] } } as any as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArtemisTestModule],
                declarations: [FeatureOverviewComponent, TranslatePipeMock],
                providers: [
                    { provide: ActivatedRoute, useValue: route },
                    { provide: ProfileService, useValue: MockProfileService },
                ],
            }).compileComponents();

            fixture = TestBed.createComponent(FeatureOverviewComponent);
            debugElement = fixture.debugElement;
            comp = fixture.componentInstance;
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
                            expect(featureA.id === featureB.id).toBeFalse();
                        }
                    }
                }
            });
        });

        describe('Navigate to Feature Details', () => {
            it('should scroll to the correct feature detail', fakeAsync(() => {
                const navigateToFeatureSpy = jest.spyOn(comp, 'navigateToFeature');
                // WHEN
                comp.ngOnInit();
                fixture.detectChanges();
                const id = '#featureOverview' + comp.features[0].id;
                tick();
                const featureOverview = debugElement.query(By.css(id));

                featureOverview.nativeElement.click();

                // THEN
                expect(navigateToFeatureSpy).toHaveBeenCalledWith(comp.features[0].id);
            }));
        });
    });
});
