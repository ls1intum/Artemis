import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed, tick, inject, fakeAsync } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { of } from 'rxjs';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { CookieService } from 'ngx-cookie';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateService } from '@ngx-translate/core';

import { ArtemisSharedModule } from 'app/shared';
import { ArtemisTestModule } from '../test.module';
import { NavbarComponent } from 'app/layouts';
import { SERVER_API_URL } from 'app/app.constants';
import { GuidedTour } from 'app/guided-tour/guided-tour.model';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { GuidedTourState, Orientation, UserInteractionEvent } from 'app/guided-tour/guided-tour.constants';
import { GuidedTourComponent } from 'app/guided-tour/guided-tour.component';
import { MockCookieService, MockSyncStorage } from '../mocks';
import { GuidedTourSetting } from 'app/guided-tour/guided-tour-setting.model';
import { TextTourStep } from 'app/guided-tour/guided-tour-step.model';
import { MockAccountService } from '../mocks/mock-account.service';
import { AccountService } from 'app/core';
import { DeviceDetectorService } from 'ngx-device-detector';
import { Course } from 'app/entities/course';
import { Exercise, ExerciseType } from 'app/entities/exercise';
import { MockTranslateService } from '../mocks/mock-translate.service';
import { GuidedTourModelingTask, personUML } from 'app/guided-tour/guided-tour-task.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('GuidedTourService', () => {
    const tour: GuidedTour = {
        courseShortName: '',
        exerciseShortName: '',
        settingsKey: 'tour',
        steps: [
            new TextTourStep({
                highlightSelector: '.random-selector',
                headlineTranslateKey: '',
                contentTranslateKey: '',
            }),
            new TextTourStep({
                headlineTranslateKey: '',
                contentTranslateKey: '',
                orientation: Orientation.TOPLEFT,
            }),
        ],
    };

    const tourWithUserInteraction: GuidedTour = {
        courseShortName: '',
        exerciseShortName: '',
        settingsKey: 'tour_user_interaction',
        steps: [
            new TextTourStep({
                highlightSelector: '.random-selector',
                headlineTranslateKey: '',
                contentTranslateKey: '',
                userInteractionEvent: UserInteractionEvent.CLICK,
            }),
            new TextTourStep({
                headlineTranslateKey: '',
                contentTranslateKey: '',
                orientation: Orientation.TOPLEFT,
            }),
        ],
    };

    const tourWithCourseAndExercise: GuidedTour = {
        courseShortName: 'tutorial',
        exerciseShortName: 'git',
        settingsKey: 'tour_with_course_and_exericse',
        steps: [
            new TextTourStep({
                headlineTranslateKey: '',
                contentTranslateKey: '',
            }),
            new TextTourStep({
                headlineTranslateKey: '',
                contentTranslateKey: '',
                orientation: Orientation.TOPLEFT,
            }),
        ],
    };

    const tourWithModelingTask: GuidedTour = {
        courseShortName: '',
        exerciseShortName: '',
        settingsKey: 'tour_modeling_task',
        preventBackdropFromAdvancing: true,
        steps: [
            new TextTourStep({
                headlineTranslateKey: '',
                contentTranslateKey: '',
                modelingTask: new GuidedTourModelingTask(personUML.name, ''),
            }),
        ],
    };

    describe('Service method', () => {
        let service: GuidedTourService;
        let httpMock: any;
        const expected = new GuidedTourSetting('guided_tour_key', 1, GuidedTourState.STARTED);

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArtemisTestModule, ArtemisSharedModule, HttpClientTestingModule],
                providers: [GuidedTourService, { provide: DeviceDetectorService }],
            });

            service = TestBed.get(GuidedTourService);
            httpMock = TestBed.get(HttpTestingController);
        });

        afterEach(() => {
            httpMock.verify();
        });

        it('should call correct update URL and return the right JSON object', () => {
            service.guidedTourSettings = [];
            service.updateGuidedTourSettings('guided_tour_key', 1, GuidedTourState.STARTED).subscribe();
            const req = httpMock.expectOne({ method: 'PUT' });
            const resourceUrl = SERVER_API_URL + 'api/guided-tour-settings';
            expect(req.request.url).equal(`${resourceUrl}`);
            expect(service.guidedTourSettings).to.eql([expected]);
        });
    });

    describe('Guided tour methods', () => {
        let guidedTourComponent: GuidedTourComponent;
        let guidedTourComponentFixture: ComponentFixture<GuidedTourComponent>;

        let navbarComponent: NavbarComponent;
        let navbarComponentFixture: ComponentFixture<NavbarComponent>;

        let guidedTourService: GuidedTourService;
        let router: Router;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [
                    ArtemisTestModule,
                    ArtemisSharedModule,
                    RouterTestingModule.withRoutes([
                        {
                            path: 'overview',
                            component: NavbarComponent,
                        },
                    ]),
                ],
                schemas: [NO_ERRORS_SCHEMA],
                declarations: [NavbarComponent, GuidedTourComponent],
                providers: [
                    { provide: LocalStorageService, useClass: MockSyncStorage },
                    { provide: SessionStorageService, useClass: MockSyncStorage },
                    { provide: CookieService, useClass: MockCookieService },
                    { provide: AccountService, useClass: MockAccountService },
                    { provide: DeviceDetectorService },
                    { provide: TranslateService, useClass: MockTranslateService },
                ],
            })
                .overrideTemplate(NavbarComponent, '<div class="random-selector"></div>')
                .compileComponents()
                .then(() => {
                    guidedTourComponentFixture = TestBed.createComponent(GuidedTourComponent);
                    guidedTourComponent = guidedTourComponentFixture.componentInstance;

                    navbarComponentFixture = TestBed.createComponent(NavbarComponent);
                    navbarComponent = navbarComponentFixture.componentInstance;

                    guidedTourService = TestBed.get(GuidedTourService);
                    router = TestBed.get(Router);
                });
        });

        function prepareGuidedTour(tour: GuidedTour) {
            // Prepare GuidedTourService and GuidedTourComponent
            spyOn(guidedTourService, 'init').and.returnValue(of());
            spyOn(guidedTourService, 'checkSelectorValidity').and.returnValue(true);
            spyOn(guidedTourService, 'checkTourState').and.returnValue(true);
            spyOn(guidedTourService, 'getLastSeenTourStepIndex').and.returnValue(0);
            spyOn(guidedTourService, 'updateGuidedTourSettings').and.returnValue(of());
            spyOn(guidedTourService, 'enableTour').and.callFake(() => {
                guidedTourService['availableTourForComponent'] = tour;
                guidedTourService.currentTour = tour;
            });
        }

        async function startCourseOverviewTour(tour: GuidedTour) {
            guidedTourComponent.ngAfterViewInit();

            await guidedTourComponentFixture.ngZone!.run(() => {
                router.navigateByUrl('/overview');
            });

            // Start course overview tour
            expect(guidedTourComponentFixture.debugElement.query(By.css('.tour-step'))).to.not.exist;
            guidedTourService.enableTour(tour);
            guidedTourService.startTour();
            guidedTourComponentFixture.detectChanges();
            expect(guidedTourComponentFixture.debugElement.query(By.css('.tour-step'))).to.exist;
            expect(guidedTourService.isOnFirstStep).to.be.true;
            expect(guidedTourService.currentTourStepDisplay).to.equal(1);
            expect(guidedTourService.currentTourStepCount).to.equal(2);
        }

        describe('Tours without user interaction', () => {
            beforeEach(async () => {
                prepareGuidedTour(tour);
                await startCourseOverviewTour(tour);
            });

            it('should start and finish the course overview guided tour', async () => {
                // Navigate to next step
                const nextButton = guidedTourComponentFixture.debugElement.query(By.css('.next-button'));
                expect(nextButton).to.exist;
                nextButton.nativeElement.click();
                expect(guidedTourService.isOnLastStep).to.be.true;

                // Finish guided tour
                nextButton.nativeElement.click();
                guidedTourComponentFixture.detectChanges();
                expect(guidedTourComponentFixture.debugElement.query(By.css('.tour-step'))).to.not.exist;
            });

            it('should start and skip the tour', () => {
                const skipButton = guidedTourComponentFixture.debugElement.query(By.css('.close'));
                expect(skipButton).to.exist;
                skipButton.nativeElement.click();
                guidedTourComponentFixture.detectChanges();
                expect(guidedTourComponentFixture.debugElement.query(By.css('.tour-step'))).to.not.exist;
            });

            it('should prevent backdrop from advancing', () => {
                const backdrop = guidedTourComponentFixture.debugElement.queryAll(By.css('.guided-tour-overlay'));
                expect(backdrop).to.exist;
                expect(backdrop.length).to.equal(4);
                backdrop.forEach(overlay => {
                    overlay.nativeElement.click();
                });
                guidedTourComponentFixture.detectChanges();
                expect(guidedTourService.isOnFirstStep).to.be.true;
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
                expect(nextButton).to.exist;
            });
        });

        describe('Tour for a certain course and exercise', () => {
            const exercise1 = {
                id: 1,
                shortName: 'git',
                type: ExerciseType.PROGRAMMING,
            } as Exercise;

            const exercise2 = {
                id: 1,
                shortName: 'test',
                type: ExerciseType.PROGRAMMING,
            } as Exercise;

            const course1 = {
                id: 1,
                shortName: 'tutorial',
                exercises: [exercise2, exercise1],
            } as Course;

            const course2 = {
                id: 1,
                shortName: 'test',
            } as Course;

            beforeEach(async () => {
                prepareGuidedTour(tourWithCourseAndExercise);
            });

            it('should start the tour for the matching course title', () => {
                let courses = [course1];
                // enable tour for matching course title
                guidedTourService.enableTourForCourse(courses, tourWithCourseAndExercise);
                expect(guidedTourService.currentTour).to.equal(tourWithCourseAndExercise);
                guidedTourService.currentTour = null;

                courses = [course2];
                // tour not available for not matching titles
                guidedTourService.enableTourForCourse(courses, tourWithCourseAndExercise);
                expect(guidedTourService.currentTour).to.be.null;
            });

            it('should start the tour for the matching exercise short name', () => {
                let courses = [course1];
                // enable tour for matching course title
                guidedTourService.enableTourForExercise(exercise1, tourWithCourseAndExercise);
                expect(guidedTourService.currentTour).to.equal(tourWithCourseAndExercise);
                guidedTourService.currentTour = null;

                courses = [course2];
                // tour not available for not matching titles
                guidedTourService.enableTourForExercise(exercise2, tourWithCourseAndExercise);
                expect(guidedTourService.currentTour).to.be.null;
            });

            it('should start the tour for the matching course / exercise short name', () => {
                // enable tour for matching course / exercise short name
                let currentExercise = guidedTourService.enableTourForCourseExerciseComponent(course1, tourWithCourseAndExercise) as Exercise;
                expect(currentExercise.shortName).to.equal(tourWithCourseAndExercise.exerciseShortName);

                // tour not available for not matching course / exercise short name
                currentExercise = guidedTourService.enableTourForCourseExerciseComponent(course2, tourWithCourseAndExercise) as Exercise;
                expect(currentExercise).to.be.null;
            });
        });

        describe('Dot calculation', () => {
            it('should calculate the n-small dot display', () => {
                // Initially the getLastSeenTourStepIndex is 0 because we don't access the user guided settings
                expect(guidedTourService.calculateNSmallDot(0)).to.be.false;
                expect(guidedTourService.calculateNSmallDot(10)).to.be.true;

                // We update the getLastSeenTourStepIndex to check whether it is called correctly if the last seen step is bigger than the max dots value
                spyOn(guidedTourService, 'getLastSeenTourStepIndex').and.returnValue(12);
                expect(guidedTourService.calculateNSmallDot(14)).to.be.true;
            });

            it('should calculate the p-small dot', () => {
                // The p-small class is not displayed if the total count of steps is smaller than the max dots value
                expect(guidedTourService.calculatePSmallDot(0)).to.be.false;
                spyOn(guidedTourService, 'getLastSeenTourStepIndex').and.returnValue(15);
                expect(guidedTourService.calculatePSmallDot(8)).to.be.true;
            });
        });

        describe('Modeling check', () => {
            it('should enable the next step if the results are correct', inject(
                [],
                fakeAsync(() => {
                    const enableNextStep = spyOn<any>(guidedTourService, 'enableNextStepClick').and.returnValue(of());
                    guidedTourService.currentTour = tourWithModelingTask;
                    guidedTourService.updateModelingResult(personUML.name, true);
                    tick(0);
                    expect(enableNextStep.calls.count()).to.equal(1);
                }),
            ));
        });
    });
});
