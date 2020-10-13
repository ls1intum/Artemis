import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { ArtemisTestModule } from '../../test.module';
import { FeatureOverviewComponent, TargetAudience } from 'app/feature-overview/feature-overview.component';
import { By } from '@angular/platform-browser';
import { DebugElement } from '@angular/core';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { spy } from 'sinon';

describe('Feature Overview Component', () => {
    let comp: FeatureOverviewComponent;
    let fixture: ComponentFixture<FeatureOverviewComponent>;
    let debugElement: DebugElement;

    describe('Target Audience: Instructors', function () {
        let route = ({ snapshot: { url: ['instructors'] } } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArtemisTestModule],
                declarations: [FeatureOverviewComponent, TranslatePipeMock],
                providers: [{ provide: ActivatedRoute, useValue: route }],
            }).compileComponents();

            fixture = TestBed.createComponent(FeatureOverviewComponent);
            debugElement = fixture.debugElement;
            comp = fixture.componentInstance;
        });

        describe('onInit', () => {
            it('should load all features for instructors', function () {
                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.targetAudience).toEqual(TargetAudience.INSTRUCTORS);
                expect(comp.features.length).toBeGreaterThan(0);
            });

            it('should ensure all features have unique IDs', function () {
                // WHEN
                comp.ngOnInit();

                // THEN
                for (let featureA of comp.features) {
                    for (let featureB of comp.features) {
                        if (featureA !== featureB) {
                            expect(featureA.id === featureB.id).toBeFalsy();
                        }
                    }
                }
            });
        });

        describe('Navigate to Feature Details', () => {
            it('should scroll to the correct feature detail', fakeAsync(() => {
                const navigateToFeatureSpy = spyOn(comp, 'navigateToFeature');
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

    describe('Target Audience: Students', function () {
        const studentRoute = ({ snapshot: { url: ['students'] } } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArtemisTestModule],
                declarations: [FeatureOverviewComponent],
                providers: [{ provide: ActivatedRoute, useValue: studentRoute }],
            })
                .overrideTemplate(FeatureOverviewComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(FeatureOverviewComponent);
            comp = fixture.componentInstance;
        });

        describe('onInit', () => {
            it('should load all features for students', function () {
                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.targetAudience).toEqual(TargetAudience.STUDENTS);
                expect(comp.features.length).toBeGreaterThan(0);
            });
        });
    });
});
