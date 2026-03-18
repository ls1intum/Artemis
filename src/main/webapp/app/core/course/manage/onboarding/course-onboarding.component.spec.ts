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
import dayjs from 'dayjs/esm';
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
    let router: MockRouter;
    let course: Course;

    beforeEach(async () => {
        course = new Course();
        course.id = 1;
        course.title = 'Test Course';
        course.shortName = 'TC';

        const route = {
            data: of({ course }),
            queryParams: of({}),
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
        it('should save and go back to the previous step', () => {
            const updatedCourse = { ...course } as Course;
            const updateSpy = vi.spyOn(courseManagementService, 'update').mockReturnValue(of(new HttpResponse({ body: updatedCourse })));

            comp.ngOnInit();
            fixture.detectChanges();
            advanceToStep(1);
            expect(comp.activeStep()).toBe(1);

            comp.previousStep();
            expect(updateSpy).toHaveBeenCalled();
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
        it('should set onboardingDone and advance to explore step', () => {
            const updatedCourse = { ...course, onboardingDone: true } as Course;
            const updateSpy = vi.spyOn(courseManagementService, 'update').mockReturnValue(of(new HttpResponse({ body: updatedCourse })));

            comp.ngOnInit();
            fixture.detectChanges();
            comp.finishSetup();

            expect(comp.course().onboardingDone).toBe(true);
            expect(updateSpy).toHaveBeenCalledWith(course.id, expect.objectContaining({ onboardingDone: true }));
            expect(comp.isSaving()).toBe(false);
            expect(comp.activeStep()).toBe(comp.totalSteps - 1);
        });

        it('should handle error during finish', () => {
            const errorResponse = new HttpErrorResponse({ status: 500, statusText: 'Server Error' });
            vi.spyOn(courseManagementService, 'update').mockReturnValue(throwError(() => errorResponse));

            comp.ngOnInit();
            fixture.detectChanges();
            comp.finishSetup();

            expect(comp.isSaving()).toBe(false);
            expect(comp.activeStep()).toBe(0);
        });
    });

    describe('goToCourse', () => {
        it('should set onboardingDone and navigate to course management when not yet done', () => {
            const updatedCourse = { ...course, onboardingDone: true } as Course;
            const updateSpy = vi.spyOn(courseManagementService, 'update').mockReturnValue(of(new HttpResponse({ body: updatedCourse })));

            comp.ngOnInit();
            fixture.detectChanges();
            comp.goToCourse();

            expect(updateSpy).toHaveBeenCalledWith(course.id, expect.objectContaining({ onboardingDone: true }));
            expect(router.navigate).toHaveBeenCalledWith(['course-management', course.id], { queryParams: { fromOnboarding: true } });
        });

        it('should navigate directly when onboardingDone is already true', () => {
            course.onboardingDone = true;
            const updateSpy = vi.spyOn(courseManagementService, 'update');

            comp.ngOnInit();
            fixture.detectChanges();
            comp.goToCourse();

            expect(updateSpy).not.toHaveBeenCalled();
            expect(router.navigate).toHaveBeenCalledWith(['course-management', course.id], { queryParams: { fromOnboarding: true } });
        });

        it('should not navigate when course has no id', () => {
            const courseWithoutId = new Course();
            comp.ngOnInit();
            fixture.detectChanges();
            comp.course.set(courseWithoutId);

            comp.goToCourse();
            expect(router.navigate).not.toHaveBeenCalled();
        });

        it('should revert onboardingDone and show error when update fails', () => {
            const errorResponse = new HttpErrorResponse({ status: 400, statusText: 'Bad Request' });
            vi.spyOn(courseManagementService, 'update').mockReturnValue(throwError(() => errorResponse));
            const alertService = TestBed.inject(AlertService);
            const errorSpy = vi.spyOn(alertService, 'error');

            comp.ngOnInit();
            fixture.detectChanges();
            comp.goToCourse();

            expect(router.navigate).not.toHaveBeenCalled();
            expect(comp.course().onboardingDone).toBe(false);
            expect(errorSpy).toHaveBeenCalled();
        });
    });

    describe('goToStep', () => {
        it('should save and navigate to a specific step', () => {
            const updatedCourse = { ...course } as Course;
            const updateSpy = vi.spyOn(courseManagementService, 'update').mockReturnValue(of(new HttpResponse({ body: updatedCourse })));

            comp.ngOnInit();
            fixture.detectChanges();

            comp.goToStep(3);
            expect(updateSpy).toHaveBeenCalledWith(course.id, course);
            expect(comp.activeStep()).toBe(3);
        });

        it('should not navigate when already on that step', () => {
            comp.ngOnInit();
            fixture.detectChanges();

            comp.goToStep(0);
            expect(comp.activeStep()).toBe(0);
        });

        it('should not navigate while saving', () => {
            comp.ngOnInit();
            fixture.detectChanges();

            const errorResponse = new HttpErrorResponse({ status: 500, statusText: 'Server Error' });
            vi.spyOn(courseManagementService, 'update').mockReturnValue(throwError(() => errorResponse));

            comp.isSaving.set(true);
            comp.goToStep(2);
            expect(comp.activeStep()).toBe(0);
        });

        it('should navigate without saving when course has no id', () => {
            comp.ngOnInit();
            fixture.detectChanges();

            const courseWithoutId = new Course();
            courseWithoutId.title = 'No ID';
            comp.course.set(courseWithoutId);

            const updateSpy = vi.spyOn(courseManagementService, 'update');
            comp.goToStep(2);

            expect(updateSpy).not.toHaveBeenCalled();
            expect(comp.activeStep()).toBe(2);
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

    describe('validateCurrentStep', () => {
        let alertService: AlertService;

        beforeEach(() => {
            alertService = TestBed.inject(AlertService);
            comp.ngOnInit();
            fixture.detectChanges();
        });

        it('should reject startDate after endDate on step 0', () => {
            const errorSpy = vi.spyOn(alertService, 'error');
            const c = comp.course();
            c.startDate = dayjs().add(1, 'day');
            c.endDate = dayjs().subtract(1, 'day');
            comp.course.set(c);

            expect(comp.validateCurrentStep()).toBe(false);
            expect(errorSpy).toHaveBeenCalledWith('artemisApp.course.onboarding.validation.startDateBeforeEndDate');
        });

        it('should accept valid dates on step 0', () => {
            const c = comp.course();
            c.startDate = dayjs().subtract(1, 'day');
            c.endDate = dayjs().add(1, 'day');
            comp.course.set(c);

            expect(comp.validateCurrentStep()).toBe(true);
        });

        it('should reject enrollment startDate after endDate on step 1', () => {
            const errorSpy = vi.spyOn(alertService, 'error');
            advanceToStep(1);

            const c = comp.course();
            c.enrollmentEnabled = true;
            c.enrollmentStartDate = dayjs().add(1, 'day');
            c.enrollmentEndDate = dayjs().subtract(1, 'day');
            comp.course.set(c);

            expect(comp.validateCurrentStep()).toBe(false);
            expect(errorSpy).toHaveBeenCalledWith('artemisApp.course.onboarding.validation.enrollmentStartDateBeforeEndDate');
        });

        it('should reject accuracyOfScores out of range on step 3', () => {
            const errorSpy = vi.spyOn(alertService, 'error');
            advanceToStep(3);

            const c = comp.course();
            c.accuracyOfScores = 10;
            comp.course.set(c);

            expect(comp.validateCurrentStep()).toBe(false);
            expect(errorSpy).toHaveBeenCalledWith('artemisApp.course.onboarding.validation.accuracyOfScoresRange');
        });

        it('should reject maxPoints <= 0 on step 3', () => {
            const errorSpy = vi.spyOn(alertService, 'error');
            advanceToStep(3);

            const c = comp.course();
            c.maxPoints = 0;
            comp.course.set(c);

            expect(comp.validateCurrentStep()).toBe(false);
            expect(errorSpy).toHaveBeenCalledWith('artemisApp.course.onboarding.validation.maxPointsPositive');
        });

        it('should not advance on nextStep if validation fails', () => {
            const c = comp.course();
            c.startDate = dayjs().add(1, 'day');
            c.endDate = dayjs().subtract(1, 'day');
            comp.course.set(c);

            const updateSpy = vi.spyOn(courseManagementService, 'update');
            comp.nextStep();

            expect(updateSpy).not.toHaveBeenCalled();
            expect(comp.activeStep()).toBe(0);
        });

        it('should not finish setup if validation fails', () => {
            advanceToStep(3);

            const c = comp.course();
            c.accuracyOfScores = -1;
            comp.course.set(c);

            const updateSpy = vi.spyOn(courseManagementService, 'update');
            updateSpy.mockClear();
            comp.finishSetup();

            expect(updateSpy).not.toHaveBeenCalled();
        });

        it('should not navigate forward via goToStep if validation fails', () => {
            const c = comp.course();
            c.startDate = dayjs().add(1, 'day');
            c.endDate = dayjs().subtract(1, 'day');
            comp.course.set(c);

            comp.goToStep(2);
            expect(comp.activeStep()).toBe(0);
        });

        it('should allow backward navigation via goToStep even if validation fails', () => {
            advanceToStep(3);

            const c = comp.course();
            c.accuracyOfScores = -1;
            comp.course.set(c);

            const updateSpy = vi.spyOn(courseManagementService, 'update').mockReturnValue(of(new HttpResponse({ body: c })));
            updateSpy.mockClear();
            comp.goToStep(1);
            expect(comp.activeStep()).toBe(1);
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
            // False on last step (Explore)
            advanceToStep(comp.totalSteps - 1);
            expect(comp.canFinish()).toBe(false);
        });
    });
});
