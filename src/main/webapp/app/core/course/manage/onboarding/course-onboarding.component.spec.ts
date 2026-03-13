import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { of, throwError } from 'rxjs';

import { CourseOnboardingComponent } from './course-onboarding.component';
import { CourseManagementService } from '../services/course-management.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { OnboardingGeneralSettingsComponent } from './pages/onboarding-general-settings.component';
import { OnboardingEnrollmentComponent } from './pages/onboarding-enrollment.component';
import { OnboardingCommunicationComponent } from './pages/onboarding-communication.component';
import { OnboardingAssessmentAiComponent } from './pages/onboarding-assessment-ai.component';
import { OnboardingExploreComponent } from './pages/onboarding-explore.component';

describe('CourseOnboardingComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: CourseOnboardingComponent;
    let fixture: ComponentFixture<CourseOnboardingComponent>;
    let courseManagementService: CourseManagementService;
    let _alertService: AlertService;
    let router: MockRouter;
    let course: Course;

    beforeEach(async () => {
        course = new Course();
        course.id = 1;
        course.title = 'Test Course';
        course.shortName = 'TC';

        const route = {
            data: of({ course }),
        } as any as ActivatedRoute;

        await TestBed.configureTestingModule({
            imports: [CourseOnboardingComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                { provide: ProfileService, useClass: MockProfileService },
                MockProvider(AlertService),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .overrideComponent(CourseOnboardingComponent, {
                remove: {
                    imports: [
                        TranslateDirective,
                        ArtemisTranslatePipe,
                        OnboardingGeneralSettingsComponent,
                        OnboardingEnrollmentComponent,
                        OnboardingCommunicationComponent,
                        OnboardingAssessmentAiComponent,
                        OnboardingExploreComponent,
                    ],
                },
                add: {
                    imports: [
                        MockDirective(TranslateDirective),
                        MockPipe(ArtemisTranslatePipe),
                        MockComponent(OnboardingGeneralSettingsComponent),
                        MockComponent(OnboardingEnrollmentComponent),
                        MockComponent(OnboardingCommunicationComponent),
                        MockComponent(OnboardingAssessmentAiComponent),
                        MockComponent(OnboardingExploreComponent),
                    ],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(CourseOnboardingComponent);
        comp = fixture.componentInstance;
        courseManagementService = TestBed.inject(CourseManagementService);
        _alertService = TestBed.inject(AlertService);
        router = TestBed.inject(Router) as unknown as MockRouter;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    /** Helper to advance to a specific step by calling nextStep repeatedly with mocked saves */
    function advanceToStep(targetStep: number) {
        const updatedCourse = { ...course } as Course;
        vi.spyOn(courseManagementService, 'update').mockReturnValue(of(new HttpResponse({ body: updatedCourse })));
        for (let i = 0; i < targetStep; i++) {
            comp.nextStep();
        }
    }

    describe('ngOnInit', () => {
        it('should load course from route data', () => {
            comp.ngOnInit();
            fixture.detectChanges();

            expect(comp.course()).toEqual(course);
        });

        it('should initialize with step 0', () => {
            comp.ngOnInit();
            fixture.detectChanges();

            expect(comp.activeStep()).toBe(0);
            expect(comp.isFirstStep()).toBe(true);
            expect(comp.isLastStep()).toBe(false);
        });
    });

    describe('nextStep', () => {
        it('should save and advance to the next step', () => {
            const updatedCourse = { ...course } as Course;
            const updateSpy = vi.spyOn(courseManagementService, 'update').mockReturnValue(of(new HttpResponse({ body: updatedCourse })));

            comp.ngOnInit();
            fixture.detectChanges();
            comp.nextStep();

            expect(updateSpy).toHaveBeenCalledWith(course.id, course);
            expect(comp.activeStep()).toBe(1);
            expect(comp.isSaving()).toBe(false);
        });

        it('should not advance past the last step', () => {
            comp.ngOnInit();
            fixture.detectChanges();

            advanceToStep(comp.totalSteps - 1);
            expect(comp.activeStep()).toBe(comp.totalSteps - 1);

            comp.nextStep();
            expect(comp.activeStep()).toBe(comp.totalSteps - 1);
        });

        it('should handle save error on next step', () => {
            const errorResponse = new HttpErrorResponse({ status: 500, statusText: 'Server Error' });
            vi.spyOn(courseManagementService, 'update').mockReturnValue(throwError(() => errorResponse));

            comp.ngOnInit();
            fixture.detectChanges();
            comp.nextStep();

            expect(comp.activeStep()).toBe(0);
            expect(comp.isSaving()).toBe(false);
        });

        it('should advance without saving when course has no id', () => {
            comp.ngOnInit();
            fixture.detectChanges();

            const courseWithoutId = new Course();
            courseWithoutId.title = 'No ID';
            comp.course.set(courseWithoutId);

            const updateSpy = vi.spyOn(courseManagementService, 'update');
            comp.nextStep();

            expect(updateSpy).not.toHaveBeenCalled();
            expect(comp.activeStep()).toBe(1);
        });
    });

    describe('previousStep', () => {
        it('should go back to the previous step', () => {
            comp.ngOnInit();
            fixture.detectChanges();
            advanceToStep(1);
            expect(comp.activeStep()).toBe(1);

            comp.previousStep();
            expect(comp.activeStep()).toBe(0);
        });

        it('should not go below step 0', () => {
            comp.ngOnInit();
            fixture.detectChanges();
            expect(comp.activeStep()).toBe(0);

            comp.previousStep();
            expect(comp.activeStep()).toBe(0);
        });
    });

    describe('finishSetup', () => {
        it('should set onboardingDone and navigate to course management', () => {
            const updatedCourse = { ...course, onboardingDone: true } as Course;
            const updateSpy = vi.spyOn(courseManagementService, 'update').mockReturnValue(of(new HttpResponse({ body: updatedCourse })));

            comp.ngOnInit();
            fixture.detectChanges();
            comp.finishSetup();

            expect(comp.course().onboardingDone).toBe(true);
            expect(updateSpy).toHaveBeenCalledWith(course.id, expect.objectContaining({ onboardingDone: true }));
            expect(comp.isSaving()).toBe(false);
            expect(router.navigate).toHaveBeenCalledWith(['course-management', course.id]);
        });

        it('should handle error during finish', () => {
            const errorResponse = new HttpErrorResponse({ status: 500, statusText: 'Server Error' });
            vi.spyOn(courseManagementService, 'update').mockReturnValue(throwError(() => errorResponse));

            comp.ngOnInit();
            fixture.detectChanges();
            comp.finishSetup();

            expect(comp.isSaving()).toBe(false);
            expect(router.navigate).not.toHaveBeenCalledWith(['course-management', course.id]);
        });
    });

    describe('onCourseUpdated', () => {
        it('should update the course signal', () => {
            comp.ngOnInit();
            fixture.detectChanges();

            const updatedCourse = new Course();
            updatedCourse.id = 1;
            updatedCourse.title = 'Updated Course';

            comp.onCourseUpdated(updatedCourse);
            expect(comp.course()).toEqual(updatedCourse);
        });
    });

    describe('computed properties', () => {
        it('should correctly compute isFirstStep', () => {
            comp.ngOnInit();
            fixture.detectChanges();

            expect(comp.isFirstStep()).toBe(true);
            advanceToStep(1);
            expect(comp.isFirstStep()).toBe(false);
        });

        it('should correctly compute isLastStep', () => {
            comp.ngOnInit();
            fixture.detectChanges();

            expect(comp.isLastStep()).toBe(false);
            advanceToStep(comp.totalSteps - 1);
            expect(comp.isLastStep()).toBe(true);
        });

        it('should correctly compute canFinish', () => {
            comp.ngOnInit();
            fixture.detectChanges();

            expect(comp.canFinish()).toBe(false);
            advanceToStep(comp.totalSteps - 2);
            expect(comp.canFinish()).toBe(true);
            // Also true on last step
            advanceToStep(comp.totalSteps - 1);
            expect(comp.canFinish()).toBe(true);
        });
    });
});
