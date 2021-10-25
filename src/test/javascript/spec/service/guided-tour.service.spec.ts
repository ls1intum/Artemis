import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { ComponentFixture, fakeAsync, inject, TestBed, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { NavigationEnd, NavigationStart, Router, RouterState } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { Observable, of } from 'rxjs';
import { CookieService } from 'ngx-cookie-service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTestModule } from '../test.module';
import { GuidedTour } from 'app/guided-tour/guided-tour.model';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { GuidedTourState, Orientation, ResetParticipation, UserInteractionEvent } from 'app/guided-tour/guided-tour.constants';
import { GuidedTourComponent } from 'app/guided-tour/guided-tour.component';
import { GuidedTourMapping, GuidedTourSetting } from 'app/guided-tour/guided-tour-setting.model';
import { AssessmentTaskTourStep, ModelingTaskTourStep, TextTourStep, UserInterActionTourStep } from 'app/guided-tour/guided-tour-step.model';
import { MockAccountService } from '../helpers/mocks/service/mock-account.service';
import { AccountService } from 'app/core/auth/account.service';
import { DeviceDetectorService } from 'ngx-device-detector';
import { Course } from 'app/entities/course.model';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { AssessmentObject, GuidedTourAssessmentTask, GuidedTourModelingTask, personUML } from 'app/guided-tour/guided-tour-task.model';
import { completedTour } from 'app/guided-tour/tours/general-tour';
import * as sinon from 'sinon';
import { SinonStub, stub } from 'sinon';
import { HttpResponse } from '@angular/common/http';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { InitializationState } from 'app/entities/participation/participation.model';
import { NavbarComponent } from 'app/shared/layouts/navbar/navbar.component';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { MockCookieService } from '../helpers/mocks/service/mock-cookie.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SafeResourceUrlPipe } from 'app/shared/pipes/safe-resource-url.pipe';
import { User } from 'app/core/user/user.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../helpers/mocks/service/mock-metis-service.service';

chai.use(sinonChai);

class MockRouterWithEvents {
    public url = 'courses';

    public events = new Observable((observer) => {
        observer.next(new NavigationStart(0, 'courses'));
        observer.next(new NavigationEnd(1, 'courses', 'courses'));
        observer.complete();
    });

    public routerState = {} as RouterState;
}

describe('GuidedTourService', () => {
    const tour: GuidedTour = {
        settingsKey: 'tour',
        resetParticipation: ResetParticipation.EXERCISE_PARTICIPATION,
        steps: [
            new TextTourStep({ highlightSelector: '.random-selector', headlineTranslateKey: '', contentTranslateKey: '' }),
            new TextTourStep({ headlineTranslateKey: '', contentTranslateKey: '', orientation: Orientation.TOPLEFT }),
        ],
    };

    const tourWithUserInteraction: GuidedTour = {
        settingsKey: 'tour_user_interaction',
        resetParticipation: ResetParticipation.EXERCISE_PARTICIPATION,
        steps: [
            new UserInterActionTourStep({
                highlightSelector: '.random-selector',
                headlineTranslateKey: '',
                contentTranslateKey: '',
                userInteractionEvent: UserInteractionEvent.CLICK,
            }),
            new TextTourStep({ headlineTranslateKey: '', contentTranslateKey: '', orientation: Orientation.TOPLEFT, pageUrl: 'courses' }),
        ],
    };

    const tourWithCourseAndExercise: GuidedTour = {
        settingsKey: 'tour_with_course_and_exercise',
        resetParticipation: ResetParticipation.EXERCISE_PARTICIPATION,
        steps: [
            new TextTourStep({ headlineTranslateKey: '', contentTranslateKey: '' }),
            new TextTourStep({ headlineTranslateKey: '', contentTranslateKey: '', orientation: Orientation.TOPLEFT }),
        ],
    };

    const tourWithModelingTask: GuidedTour = {
        settingsKey: 'tour_modeling_task',
        resetParticipation: ResetParticipation.EXERCISE_PARTICIPATION,
        steps: [
            new ModelingTaskTourStep({
                headlineTranslateKey: '',
                contentTranslateKey: '',
                modelingTask: new GuidedTourModelingTask(personUML.name, ''),
                userInteractionEvent: UserInteractionEvent.MODELING,
            }),
        ],
    };

    describe('Service method', () => {
        let service: GuidedTourService;
        let httpMock: HttpTestingController;
        const expected = new GuidedTourSetting('guided_tour_key', 1, GuidedTourState.STARTED);

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArtemisTestModule, HttpClientTestingModule],
                providers: [
                    { provide: LocalStorageService, useClass: MockSyncStorage },
                    { provide: SessionStorageService, useClass: MockSyncStorage },
                    { provide: MetisService, useClass: MockMetisService },
                    MockProvider(DeviceDetectorService),
                    MockProvider(TranslateService),
                ],
            }).compileComponents();

            service = TestBed.inject(GuidedTourService);
            httpMock = TestBed.inject(HttpTestingController);
        });

        afterEach(() => {
            httpMock.verify();
        });

        it('should call the correct update URL and return the right JSON object', () => {
            service.guidedTourSettings = [];
            service['updateGuidedTourSettings']('guided_tour_key', 1, GuidedTourState.STARTED).subscribe();
            const req = httpMock.expectOne({ method: 'PUT' });
            const resourceUrl = SERVER_API_URL + 'api/guided-tour-settings';
            expect(req.request.url).toEqual(`${resourceUrl}`);
            expect(service.guidedTourSettings).toEqual([expected]);
        });

        it('should call the correct delete URL', () => {
            service.guidedTourSettings = [new GuidedTourSetting('guided_tour_key', 1, GuidedTourState.STARTED)];
            service['deleteGuidedTourSetting']('guided_tour_key').subscribe();
            const req = httpMock.expectOne({ method: 'DELETE' });
            const resourceUrl = SERVER_API_URL + 'api/guided-tour-settings';
            expect(req.request.url).toEqual(`${resourceUrl}/guided_tour_key`);
            expect(service.guidedTourSettings).toEqual([]);
        });
    });

    describe('Guided tour methods', () => {
        let guidedTourComponent: GuidedTourComponent;
        let guidedTourComponentFixture: ComponentFixture<GuidedTourComponent>;
        let router: Router;
        let guidedTourService: GuidedTourService;
        let participationService: ParticipationService;
        let courseService: CourseManagementService;

        let findParticipationStub: jest.SpyInstance;
        let deleteParticipationStub: jest.SpyInstance;
        let deleteGuidedTourSettingStub: jest.SpyInstance;
        let navigationStub: jest.SpyInstance;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [
                    ArtemisTestModule,
                    RouterTestingModule.withRoutes([
                        {
                            path: 'courses',
                            component: NavbarComponent,
                        },
                    ]),
                ],
                declarations: [NavbarComponent, GuidedTourComponent, MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective), MockPipe(SafeResourceUrlPipe)],
                providers: [
                    { provide: LocalStorageService, useClass: MockSyncStorage },
                    { provide: SessionStorageService, useClass: MockSyncStorage },
                    { provide: CookieService, useClass: MockCookieService },
                    { provide: AccountService, useClass: MockAccountService },
                    MockProvider(DeviceDetectorService),
                    { provide: TranslateService, useClass: MockTranslateService },
                    { provide: MetisService, useClass: MockMetisService },
                ],
            })
                .overrideTemplate(NavbarComponent, '<div class="random-selector"></div>')
                .compileComponents()
                .then(() => {
                    guidedTourComponentFixture = TestBed.createComponent(GuidedTourComponent);
                    guidedTourComponent = guidedTourComponentFixture.componentInstance;

                    TestBed.createComponent(NavbarComponent);

                    router = TestBed.inject(Router);
                    guidedTourService = TestBed.inject(GuidedTourService);
                    participationService = TestBed.inject(ParticipationService);
                    courseService = TestBed.inject(CourseManagementService);

                    findParticipationStub = jest.spyOn(participationService, 'findParticipation');
                    deleteParticipationStub = jest.spyOn(participationService, 'deleteForGuidedTour');
                    // @ts-ignore
                    deleteGuidedTourSettingStub = jest.spyOn(guidedTourService, 'deleteGuidedTourSetting');
                    navigationStub = jest.spyOn(router, 'navigateByUrl');
                });
        });

        function prepareGuidedTour(guidedTour: GuidedTour) {
            // Prepare GuidedTourService and GuidedTourComponent
            jest.spyOn(guidedTourService, 'init').mockImplementation();
            jest.spyOn(guidedTourService, 'getLastSeenTourStepIndex').mockReturnValue(0);
            jest.spyOn<any, any>(guidedTourService, 'checkSelectorValidity').mockReturnValue(true);
            jest.spyOn<any, any>(guidedTourService, 'checkTourState').mockReturnValue(true);
            jest.spyOn<any, any>(guidedTourService, 'updateGuidedTourSettings').mockReturnValue(of());
            jest.spyOn<any, any>(guidedTourService, 'enableTour').mockImplementation(() => {
                guidedTourService['availableTourForComponent'] = guidedTour;
                guidedTourService.currentTour = guidedTour;
            });
            jest.spyOn<any, any>(guidedTourComponent, 'subscribeToDotChanges').mockImplementation(() => {});
        }

        async function startCourseOverviewTour(guidedTour: GuidedTour) {
            guidedTourComponent.ngAfterViewInit();

            guidedTourComponentFixture.ngZone!.run(() => {
                router.navigateByUrl('/courses');
            });

            // Start course overview tour
            expect(guidedTourComponentFixture.debugElement.query(By.css('.tour-step'))).toBeNull();
            guidedTourService['enableTour'](guidedTour, true);
            guidedTourService['startTour']();
            guidedTourComponentFixture.detectChanges();
            expect(guidedTourComponentFixture.debugElement.query(By.css('.tour-step'))).toBeDefined();
            expect(guidedTourService.isOnFirstStep).toEqual(true);
            expect(guidedTourService.currentTourStepDisplay).toEqual(1);
            expect(guidedTourService.currentTourStepCount).toEqual(2);
        }

        describe('Tours without user interaction', () => {
            beforeEach(async () => {
                prepareGuidedTour(tour);
                await startCourseOverviewTour(tour);
            });

            it('should start and finish the course overview guided tour', async () => {
                // Navigate to next step
                const nextButton = guidedTourComponentFixture.debugElement.query(By.css('.next-button'));
                expect(nextButton).toBeDefined();
                nextButton.nativeElement.click();
                expect(guidedTourService.isOnLastStep).toEqual(true);

                // Finish guided tour
                nextButton.nativeElement.click();
                guidedTourComponentFixture.detectChanges();
                const tourStep = guidedTourComponentFixture.debugElement.query(By.css('.tour-step'));
                expect(tourStep).toBeNull();
            });

            it('should start and skip the tour', () => {
                const skipButton = guidedTourComponentFixture.debugElement.query(By.css('.btn-close'));
                expect(skipButton).toBeDefined();
                skipButton.nativeElement.click();
                guidedTourComponentFixture.detectChanges();
                const tourStep = guidedTourComponentFixture.debugElement.query(By.css('.tour-step'));
                expect(tourStep).toBeNull();
            });

            it('should prevent backdrop from advancing', () => {
                const backdrop = guidedTourComponentFixture.debugElement.queryAll(By.css('.guided-tour-overlay'));
                expect(backdrop).toBeDefined();
                expect(backdrop.length).toEqual(4);
                backdrop.forEach((overlay) => {
                    overlay.nativeElement.click();
                });
                guidedTourComponentFixture.detectChanges();
                expect(guidedTourService.isOnFirstStep).toEqual(true);
            });
        });

        describe('Tours with user interaction', () => {
            beforeEach(async () => {
                prepareGuidedTour(tourWithUserInteraction);
                await startCourseOverviewTour(tourWithUserInteraction);
            });

            it('should disable the next button', () => {
                guidedTourComponentFixture.detectChanges();
                const nextButton = guidedTourComponentFixture.debugElement.nativeElement.querySelector('.next-button').disabled;
                expect(nextButton).toBeDefined();
            });
        });

        describe('Tour for a certain course and exercise', () => {
            const guidedTourMapping = { courseShortName: 'tutorial', tours: { tour_with_course_and_exercise: 'git' } } as GuidedTourMapping;
            const exercise1 = { id: 1, shortName: 'git', type: ExerciseType.PROGRAMMING } as Exercise;
            const exercise2 = { id: 2, shortName: 'test', type: ExerciseType.PROGRAMMING } as Exercise;
            const exercise3 = { id: 3, shortName: 'git', type: ExerciseType.MODELING } as Exercise;
            const course1 = { id: 1, shortName: 'tutorial', exercises: [exercise2, exercise1] } as Course;
            const course2 = { id: 2, shortName: 'test' } as Course;

            function resetCurrentTour(): void {
                guidedTourService['currentCourse'] = undefined;
                guidedTourService['currentExercise'] = undefined;
                guidedTourService.currentTour = completedTour;
                guidedTourService.resetTour();
            }

            function currentCourseAndExerciseUndefined(): void {
                expect(guidedTourService.currentTour).toBeUndefined();
                expect(guidedTourService['currentCourse']).toBeUndefined();
                expect(guidedTourService['currentExercise']).toBeUndefined();
            }

            beforeEach(async () => {
                guidedTourService.guidedTourMapping = guidedTourMapping;
                prepareGuidedTour(tourWithCourseAndExercise);
                resetCurrentTour();
            });

            it('should start the tour for the matching course title', () => {
                jest.spyOn(courseService, 'findWithExercises').mockReturnValue(of({ body: course1 } as HttpResponse<any>));
                const courses = [course1];

                // enable tour for matching course title
                guidedTourService.enableTourForCourseOverview(courses, tourWithCourseAndExercise, true);
                expect(guidedTourService.currentTour).toEqual(tourWithCourseAndExercise);
                expect(guidedTourService['currentCourse']).toEqual(course1);
                expect(guidedTourService['currentExercise']).toEqual(exercise1);
                resetCurrentTour();

                guidedTourService.guidedTourMapping = { courseShortName: 'tutorial', tours: { tour_with_course_and_exercise: '' } } as GuidedTourMapping;

                // enable tour for matching course title
                guidedTourService.enableTourForCourseOverview(courses, tourWithCourseAndExercise, true);
                expect(guidedTourService.currentTour).toEqual(tourWithCourseAndExercise);
                expect(guidedTourService['currentCourse']).toEqual(course1);
                expect(guidedTourService['currentExercise']).toBeUndefined();
                resetCurrentTour();
            });

            it('should disable the tour for not matching course title', () => {
                const courses = [course2];
                // disable tour for not matching titles
                guidedTourService.enableTourForCourseOverview(courses, tourWithCourseAndExercise, true);
                currentCourseAndExerciseUndefined();
            });

            it('should start the tour for the matching exercise short name', () => {
                // disable tour for exercises without courses
                guidedTourService.currentTour = undefined;
                guidedTourService.enableTourForExercise(exercise1, tourWithCourseAndExercise, true);
                currentCourseAndExerciseUndefined();
                resetCurrentTour();

                // disable tour for not matching course and exercise identifiers
                exercise2.course = course2;
                guidedTourService.enableTourForExercise(exercise2, tourWithCourseAndExercise, true);
                currentCourseAndExerciseUndefined();
                resetCurrentTour();

                // disable tour for not matching course identifier
                exercise3.course = course2;
                guidedTourService.enableTourForExercise(exercise3, tourWithCourseAndExercise, true);
                currentCourseAndExerciseUndefined();
                resetCurrentTour();

                // enable tour for matching course and exercise identifiers
                exercise1.course = course1;
                guidedTourService.enableTourForExercise(exercise1, tourWithCourseAndExercise, true);
                expect(guidedTourService.currentTour).toEqual(tourWithCourseAndExercise);
                expect(guidedTourService['currentCourse']).toEqual(course1);
                expect(guidedTourService['currentExercise']).toEqual(exercise1);
            });

            it('should start the tour for the matching course / exercise short name', () => {
                guidedTourService.currentTour = undefined;

                // enable tour for matching course / exercise short name
                guidedTourService.enableTourForCourseExerciseComponent(course1, tourWithCourseAndExercise, true);
                expect(guidedTourService.currentTour).toEqual(tourWithCourseAndExercise);

                course1.exercises!.forEach((exercise) => {
                    exercise.course = course1;
                    if (exercise === exercise1) {
                        expect(guidedTourService['isGuidedTourAvailableForExercise'](exercise)).toEqual(true);
                    } else {
                        expect(guidedTourService['isGuidedTourAvailableForExercise'](exercise)).toEqual(false);
                    }
                });

                // disable tour for not matching course without exercise
                guidedTourService.currentTour = undefined;
                guidedTourService.enableTourForCourseExerciseComponent(course2, tourWithCourseAndExercise, true);
                expect(guidedTourService.currentTour).toBeUndefined();

                // disable tour for not matching course but matching exercise identifier
                guidedTourService.currentTour = undefined;
                course2.exercises = [exercise3];
                guidedTourService.enableTourForCourseExerciseComponent(course2, tourWithCourseAndExercise, true);
                expect(guidedTourService.currentTour).toBeUndefined();
            });

            describe('Tour with student participation', () => {
                const studentParticipation1 = { id: 1, student: { id: 1 }, exercise: exercise1, initializationState: InitializationState.INITIALIZED } as StudentParticipation;
                const studentParticipation2 = { id: 2, student: { id: 1 }, exercise: exercise3, initializationState: InitializationState.INITIALIZED } as StudentParticipation;
                const httpResponse1 = { body: studentParticipation1 } as HttpResponse<StudentParticipation>;
                const httpResponse2 = { body: studentParticipation2 } as HttpResponse<StudentParticipation>;
                const exercise4 = { id: 4, title: 'git', type: ExerciseType.MODELING } as Exercise;

                function prepareParticipation(exercise: Exercise, studentParticipation: StudentParticipation, httpResponse: HttpResponse<StudentParticipation>) {
                    exercise.course = course1;
                    exercise.studentParticipations = [studentParticipation];

                    navigationStub.mockClear();

                    findParticipationStub.mockClear();
                    findParticipationStub.mockReturnValue(of(httpResponse));

                    deleteParticipationStub.mockClear();
                    deleteParticipationStub.mockReturnValue(of(undefined));

                    deleteGuidedTourSettingStub.mockClear();
                    deleteGuidedTourSettingStub.mockReturnValue(of(undefined));
                }

                it('should find and delete the student participation for exercise', () => {
                    course1.exercises!.push(exercise4);

                    prepareParticipation(exercise1, studentParticipation1, httpResponse1);
                    guidedTourService.enableTourForExercise(exercise1, tourWithCourseAndExercise, true);
                    guidedTourService.restartTour();
                    expect(findParticipationStub).toHaveBeenCalledTimes(1);
                    expect(findParticipationStub).toHaveBeenCalledWith(1);
                    expect(deleteParticipationStub).toHaveBeenCalledTimes(1);
                    expect(deleteParticipationStub).toHaveBeenCalledWith(1, { deleteBuildPlan: true, deleteRepository: true });
                    expect(deleteGuidedTourSettingStub).toHaveBeenCalledTimes(1);
                    expect(deleteGuidedTourSettingStub).toHaveBeenCalledWith('tour_with_course_and_exercise');
                    expect(navigationStub).toHaveBeenCalledTimes(1);
                    expect(navigationStub).toHaveBeenCalledWith('/courses/1/exercises');

                    prepareParticipation(exercise4, studentParticipation2, httpResponse2);
                    guidedTourService.enableTourForExercise(exercise4, tourWithCourseAndExercise, true);
                    guidedTourService.restartTour();
                    expect(findParticipationStub).toHaveBeenCalledTimes(1);
                    expect(findParticipationStub).toHaveBeenCalledWith(4);
                    expect(deleteParticipationStub).toHaveBeenCalledTimes(1);
                    expect(deleteParticipationStub).toHaveBeenCalledWith(2, { deleteBuildPlan: false, deleteRepository: false });
                    expect(deleteGuidedTourSettingStub).toHaveBeenCalledTimes(1);
                    expect(deleteGuidedTourSettingStub).toHaveBeenCalledWith('tour_with_course_and_exercise');
                    expect(navigationStub).toHaveBeenCalledTimes(1);
                    expect(navigationStub).toHaveBeenCalledWith('/courses/1/exercises');

                    const index = course1.exercises!.findIndex((exercise) => (exercise.id = exercise4.id));
                    course1.exercises!.splice(index, 1);
                });
            });
        });

        describe('Modeling check', () => {
            it('should enable the next step if the results are correct', inject(
                [],
                fakeAsync(() => {
                    const enableNextStep = jest.spyOn<any, any>(guidedTourService, 'enableNextStepClick').mockImplementation();
                    guidedTourService.currentTour = tourWithModelingTask;
                    guidedTourService.updateModelingResult(personUML.name, true);
                    tick(0);
                    expect(enableNextStep).toHaveBeenCalledTimes(1);
                }),
            ));
        });
        describe('getGuidedTourAvailabilityStream', () => {});
        describe('checkModelingComponent', () => {});
        describe('updateModelingResult', () => {});
        describe('componentPageLoaded', () => {});

        describe('isCurrentStep', () => {
            const step1 = new TextTourStep({ highlightSelector: '.random-selector', headlineTranslateKey: '', contentTranslateKey: '' });
            const step2 = new TextTourStep({ headlineTranslateKey: '', contentTranslateKey: '', orientation: Orientation.TOPLEFT });
            const guidedTour: GuidedTour = {
                settingsKey: 'tour2',
                resetParticipation: ResetParticipation.EXERCISE_PARTICIPATION,
                steps: [step1, step2],
            };
            it('should return true if it is the current Step', () => {
                guidedTourService.currentTour = guidedTour;
                expect(guidedTourService.isCurrentStep(step1)).toEqual(true);
                guidedTourService.currentTourStepIndex += 1;
                expect(guidedTourService.isCurrentStep(step2)).toEqual(true);
            });
            it('should return false if it is not the current Step', () => {
                guidedTourService.currentTour = guidedTour;
                expect(guidedTourService.isCurrentStep(step2)).toEqual(false);
                guidedTourService.currentTourStepIndex += 1;
                expect(guidedTourService.isCurrentStep(step1)).toEqual(false);
            });
            it('should return false if current Tour is undefined', () => {
                expect(guidedTourService.isCurrentStep(step1)).toEqual(false);
            });
        });

        describe('isCurrentTour', () => {
            it('should return true if it is the current Tour', () => {
                guidedTourService.currentTour = tour;
                expect(guidedTourService.isCurrentTour(tour)).toEqual(true);
            });
            it('should return false if it is not the current Tour', () => {
                expect(guidedTourService.isCurrentTour(tour)).toEqual(false);
            });
            it('should return false if the current Tour is undefined', () => {
                guidedTourService.currentTour = tourWithCourseAndExercise;
                expect(guidedTourService.isCurrentTour(tour)).toEqual(false);
            });
        });

        describe('getCurrentStepString', () => {
            it('should return nothing if currentTour is undefined', () => {
                expect(guidedTourService.getCurrentStepString()).toBeUndefined();
            });
            it('should return correct string if currentTour is defined', () => {
                guidedTourService.currentTour = tour;
                expect(guidedTourService.getCurrentStepString()).toEqual('1 / 2');
            });
        });

        describe('backStep, nextStep', () => {
            let currentDotSubjectSpy: any;
            let resetSpy: any;

            beforeEach(() => {
                currentDotSubjectSpy = jest.spyOn<any, any>(guidedTourService.currentDotSubject, 'next');
                resetSpy = jest.spyOn<any, any>(guidedTourService, 'resetTour');
            });
            afterEach(() => {
                jest.clearAllMocks();
            });
            it('backStep should just return if currentTour is not defined', () => {
                guidedTourService.backStep();
                expect(currentDotSubjectSpy).toHaveBeenCalledTimes(0);
                expect(resetSpy).toHaveBeenCalledTimes(0);
            });
            it('backStep should reset tour if currentTour is defined', () => {
                guidedTourService.currentTour = tour;
                guidedTourService.backStep();
                expect(currentDotSubjectSpy).toHaveBeenCalledTimes(1);
                expect(resetSpy).toHaveBeenCalledTimes(1);
            });
            it('nextStep should just return if currentTour is not defined', () => {
                guidedTourService.nextStep();
                expect(currentDotSubjectSpy).toHaveBeenCalledTimes(0);
                expect(resetSpy).toHaveBeenCalledTimes(0);
            });
            it('nextStep should reset return if currentTour is defined', () => {
                guidedTourService.currentTour = tour;
                guidedTourService.nextStep();
                expect(currentDotSubjectSpy).toHaveBeenCalledTimes(1);
            });
            it('nextStep and backStep should return to initial step', fakeAsync(() => {
                guidedTourService.currentTour = tour;
                const initialStep = guidedTourService.currentTourStepIndex;
                guidedTourService.nextStep();
                tick();
                guidedTourService.backStep();
                tick();
                expect(currentDotSubjectSpy).toHaveBeenCalledTimes(2);
                expect(guidedTourService.currentTourStepIndex).toEqual(initialStep);
            }));
        });
        describe('finishGuidedTour', () => {
            it('should just return if currentTour is not defined', () => {
                guidedTourService.finishGuidedTour();
            });
        });
        describe('skipTour', () => {
            it('should just return if currentTour is not defined', () => {
                guidedTourService.skipTour();
            });
        });
        describe('subscribeToAndUpdateGuidedTourSettings', () => {});
        describe('getLastSeenTourStepIndex', () => {});
        describe('resetTour', () => {});

        describe('enableUserInteraction', () => {
            const addEventListener = jest.fn();
            const htmlTarget = { addEventListener } as any;
            let observeMutationsStub: SinonStub;
            let handleWaitForSelectorEventSpy: jest.SpyInstance;
            let querySelectorSpy: jest.SpyInstance;

            beforeEach(() => {
                guidedTourService.currentTour = tour;
                observeMutationsStub = stub(guidedTourService, 'observeMutations');
                handleWaitForSelectorEventSpy = jest.spyOn<any, any>(guidedTourService, 'handleWaitForSelectorEvent');
                querySelectorSpy = jest.spyOn(document, 'querySelector');
                querySelectorSpy.mockClear();
            });
            afterEach(() => {
                jest.clearAllMocks();
                sinon.restore();
            });
            it('should enableUserInteraction with UserInteractionEvent.WAIT_FOR_SELECTOR', fakeAsync(() => {
                const userinteractionEvent = UserInteractionEvent.WAIT_FOR_SELECTOR;
                guidedTourService.enableUserInteraction({} as any, userinteractionEvent);
                expect(handleWaitForSelectorEventSpy).toHaveBeenCalledTimes(1);
                expect(querySelectorSpy).toHaveBeenCalledTimes(0);
            }));
            it('should enableUserInteraction with UserInteractionEvent.CLICK', fakeAsync(() => {
                const userinteractionEvent = UserInteractionEvent.CLICK;
                guidedTourService.enableUserInteraction(htmlTarget, userinteractionEvent);
                expect(querySelectorSpy).toHaveBeenCalledTimes(0);
            }));
            it('should enableUserInteraction with UserInteractionEvent.ACE_EDITOR', fakeAsync(() => {
                const userinteractionEvent = UserInteractionEvent.ACE_EDITOR;
                observeMutationsStub.returns(of({ addedNodes: { length: 0 } as NodeList, removedNodes: { length: 0 } as NodeList } as MutationRecord));
                guidedTourService.enableUserInteraction(htmlTarget, userinteractionEvent);
                expect(querySelectorSpy).toHaveBeenCalledTimes(1);
            }));
            it('should enableUserInteraction with UserInteractionEvent.MODELING', fakeAsync(() => {
                const userinteractionEvent = UserInteractionEvent.MODELING;
                observeMutationsStub.returns(of({ addedNodes: { length: 0 } as NodeList, removedNodes: { length: 0 } as NodeList } as MutationRecord));
                guidedTourService.enableUserInteraction(htmlTarget, userinteractionEvent);
                expect(querySelectorSpy).toHaveBeenCalledTimes(1);
            }));
            it('should enableUserInteraction with UserInteractionEvent.ASSESS_SUBMISSION', fakeAsync(() => {
                const isAssessmentCorrectSpy = jest.spyOn<any, any>(guidedTourService, 'isAssessmentCorrect');
                const userinteractionEvent = UserInteractionEvent.ASSESS_SUBMISSION;
                guidedTourService.enableUserInteraction(htmlTarget, userinteractionEvent);
                expect(isAssessmentCorrectSpy).toHaveBeenCalledTimes(1);
                expect(querySelectorSpy).toHaveBeenCalledTimes(0);
            }));
        });

        describe('observeMutations', () => {});
        describe('initGuidedTour', () => {});
        describe('restartTour', () => {});
        describe('preventBackdropFromAdvancing', () => {});
        describe('enableTourForCourseExerciseComponent', () => {});
        describe('enableTourForCourseOverview', () => {});

        describe('enableTourForExercise', () => {
            const exerciseText = { id: 456, course: { id: 123 } as Course, type: ExerciseType.TEXT } as Exercise;
            const exerciseProgramming = { id: 456, course: { id: 123 } as Course, type: ExerciseType.PROGRAMMING } as Exercise;
            const guidedTourMapping = { courseShortName: 'tutorial', tours: { tour_with_course_and_exercise: 'git' } } as GuidedTourMapping;
            let enableTourSpy: any;
            let startTourSpy: any;

            const guidedTourSettings = [new GuidedTourSetting('guided_tour_key', 1, GuidedTourState.STARTED)];

            beforeEach(() => {
                enableTourSpy = jest.spyOn<any, any>(guidedTourService, 'enableTour').mockImplementation();
                startTourSpy = jest.spyOn<any, any>(guidedTourService, 'startTour').mockImplementation();
                jest.spyOn<any, any>(guidedTourService, 'checkTourState').mockImplementation();
                guidedTourService.guidedTourMapping = guidedTourMapping;
                guidedTourService.guidedTourSettings = [];
            });
            afterEach(() => {
                jest.clearAllMocks();
            });
            describe('return undefined if parameters are undefined', () => {
                it('should return undefined if exercise.course is undefibed', fakeAsync(() => {
                    const inputExercise = {} as Exercise;
                    expect(guidedTourService.enableTourForExercise(inputExercise, tour, true)).toBeUndefined();
                    expect(enableTourSpy).toHaveBeenCalledTimes(0);
                }));
                it('should return undefined if guidedTour mapping is undefined', fakeAsync(() => {
                    guidedTourService.guidedTourMapping = undefined;
                    expect(guidedTourService.enableTourForExercise(exerciseText, tour, true)).toBeUndefined();
                }));
            });
            it('should enableTourForExercise for text exercise', fakeAsync(() => {
                expect(guidedTourService.enableTourForExercise(exerciseText, tour, true)).toEqual(exerciseText);
                expect(enableTourSpy).toHaveBeenCalledTimes(1);
                expect(startTourSpy).toHaveBeenCalledTimes(0);
            }));
            it('should enableTourForExercise for text exercise', fakeAsync(() => {
                expect(guidedTourService.enableTourForExercise(exerciseText, tour, true)).toEqual(exerciseText);
                expect(enableTourSpy).toHaveBeenCalledTimes(1);
                expect(startTourSpy).toHaveBeenCalledTimes(0);
            }));
            it('should enableTourForExercise for programming exercise', fakeAsync(() => {
                expect(guidedTourService.enableTourForExercise(exerciseProgramming, tour, true)).toEqual(exerciseProgramming);
                expect(enableTourSpy).toHaveBeenCalledTimes(1);
                expect(startTourSpy).toHaveBeenCalledTimes(0);
            }));
            it('should enableTourForExercise for text exercise with init set to false', fakeAsync(() => {
                guidedTourService.guidedTourSettings = guidedTourSettings;
                expect(guidedTourService.enableTourForExercise(exerciseText, tour, false)).toEqual(exerciseText);
                expect(enableTourSpy).toHaveBeenCalledTimes(1);
                expect(startTourSpy).toHaveBeenCalledTimes(0);
            }));
        });

        describe('updateAssessmentResult', () => {
            let tourWithAssessmentTourSteps: GuidedTour;
            let tourWithAssessmentTourStep: GuidedTour;
            let enableNextStepSpy: any;

            beforeEach(() => {
                const assessmentObject = new AssessmentObject(2, 3);
                const assessmentObjectScoreZero = new AssessmentObject(2, 0);
                const assessmentTask = new GuidedTourAssessmentTask('t', assessmentObject);
                tourWithAssessmentTourSteps = {
                    settingsKey: 'tour',
                    resetParticipation: ResetParticipation.EXERCISE_PARTICIPATION,
                    steps: [
                        { assessmentTask } as AssessmentTaskTourStep,
                        new TextTourStep({ highlightSelector: '.random-selector', headlineTranslateKey: '', contentTranslateKey: '' }),
                        new TextTourStep({ headlineTranslateKey: '', contentTranslateKey: '', orientation: Orientation.TOPLEFT }),
                    ],
                };
                tourWithAssessmentTourStep = {
                    settingsKey: 'tour',
                    resetParticipation: ResetParticipation.EXERCISE_PARTICIPATION,
                    steps: [{ assessmentTask: new GuidedTourAssessmentTask('t', assessmentObjectScoreZero) } as AssessmentTaskTourStep],
                };
                enableNextStepSpy = jest.spyOn<any, any>(guidedTourService, 'enableNextStepClick').mockImplementation();
            });
            afterEach(() => {
                jest.clearAllMocks();
            });
            it('should updateAssessmentResult and enableNextStepClick', fakeAsync(() => {
                guidedTourService.currentTour = tourWithAssessmentTourSteps;
                guidedTourService.updateAssessmentResult(2, 3);
                tick(0);
                expect(enableNextStepSpy).toHaveBeenCalledTimes(1);
            }));
            it('should updateAssessmentResult and not enableNextStepClick as number of assessments is not correct', fakeAsync(() => {
                guidedTourService.currentTour = tourWithAssessmentTourSteps;
                guidedTourService.updateAssessmentResult(3, 3);
                tick(0);
                expect(enableNextStepSpy).toHaveBeenCalledTimes(0);
            }));
            it('should updateAssessmentResult and not enableNextStepClick as score not correct', fakeAsync(() => {
                guidedTourService.currentTour = tourWithAssessmentTourSteps;
                guidedTourService.updateAssessmentResult(2, 1);
                tick(0);
                expect(enableNextStepSpy).toHaveBeenCalledTimes(0);
            }));
            it('should not updateAssessmentResult as there is no assessmentTask', fakeAsync(() => {
                guidedTourService.currentTour = tour;
                guidedTourService.updateAssessmentResult(2, 1);
                tick(0);
                expect(enableNextStepSpy).toHaveBeenCalledTimes(0);
            }));
            it('should not updateAssessmentResult as the totalScore is 0', fakeAsync(() => {
                guidedTourService.currentTour = tourWithAssessmentTourStep;
                guidedTourService.updateAssessmentResult(2, 0);
                tick(0);
                expect(enableNextStepSpy).toHaveBeenCalledTimes(1);
            }));
        });
    });

    describe('GuidedTourService init', () => {
        let guidedTourComponentFixture: ComponentFixture<GuidedTourComponent>;
        let accountService: AccountService;
        let profileService: ProfileService;
        let guidedTourService: GuidedTourService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [
                    ArtemisTestModule,
                    RouterTestingModule.withRoutes([
                        {
                            path: 'courses',
                            component: NavbarComponent,
                        },
                    ]),
                ],
                declarations: [NavbarComponent, GuidedTourComponent, MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective), MockPipe(SafeResourceUrlPipe)],
                providers: [
                    { provide: LocalStorageService, useClass: MockSyncStorage },
                    { provide: SessionStorageService, useClass: MockSyncStorage },
                    { provide: CookieService, useClass: MockCookieService },
                    { provide: AccountService, useClass: MockAccountService },
                    MockProvider(DeviceDetectorService),
                    { provide: TranslateService, useClass: MockTranslateService },
                    { provide: Router, useClass: MockRouterWithEvents },
                    { provide: MetisService, useClass: MockMetisService },
                ],
            })
                .overrideTemplate(NavbarComponent, '<div class="random-selector"></div>')
                .compileComponents()
                .then(() => {
                    guidedTourComponentFixture = TestBed.createComponent(GuidedTourComponent);
                    TestBed.createComponent(NavbarComponent);
                    guidedTourService = TestBed.inject(GuidedTourService);
                    accountService = TestBed.inject(AccountService);
                    profileService = TestBed.inject(ProfileService);
                });
        });

        it('should initialize', fakeAsync(() => {
            const tourSettings = [{ guidedTourKey: 'test', guidedTourStep: 0 } as GuidedTourSetting];
            const authStateMock = jest.spyOn(accountService, 'getAuthenticationState').mockReturnValue(of({ guidedTourSettings: tourSettings } as User));
            const tourMapping = { courseShortName: 'test-course' } as GuidedTourMapping;
            const profileInfoMock = jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(of({ guidedTourMapping: tourMapping } as ProfileInfo));

            // Fake mapping and settings to enable the tour. Should be overwritten by the return value of the profile service
            guidedTourService.guidedTourMapping = { courseShortName: 'test', tours: { tour_user_interaction: '' } } as GuidedTourMapping;
            guidedTourService.guidedTourSettings = [{ guidedTourKey: 'test-2', guidedTourStep: 0 } as GuidedTourSetting];
            guidedTourService.enableTourForCourseOverview([{ id: 1, shortName: 'test' } as Course], tourWithUserInteraction, true);
            guidedTourService.currentTour = tourWithUserInteraction;
            tick(500);
            guidedTourService.currentTourStepIndex = 0;

            // guidedTourService.init() is called via the component initialization
            guidedTourComponentFixture.detectChanges();

            expect(authStateMock).toHaveBeenCalledTimes(1);
            expect(guidedTourService.guidedTourSettings).toEqual(tourSettings);
            expect(profileInfoMock).toHaveBeenCalledTimes(1);
            expect(guidedTourService.guidedTourMapping).toEqual(tourMapping);
        }));
    });
});
