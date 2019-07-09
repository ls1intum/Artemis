import { ComponentFixture, TestBed, fakeAsync, tick, flush } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { AccountService, JhiLanguageHelper } from 'app/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as moment from 'moment';
import { stub, SinonStub } from 'sinon';
import { ArTEMiSTestModule } from '../../test.module';
import { MockActivatedRoute, MockSyncStorage } from '../../mocks';
import { UpdatingResultComponent } from 'app/entities/result';
import { MockComponent } from 'ng-mocks';
import { ArTEMiSSharedModule } from 'app/shared';
import { ExerciseService, ExerciseType } from 'app/entities/exercise';
import { MockAlertService } from '../../helpers/mock-alert.service';
import { JhiAlertService } from 'ng-jhipster';
import { ActivatedRoute, Router, RouterModule, Params } from '@angular/router';
import { MockRouter } from '../../mocks/mock-router.service';
import { TutorParticipationStatus } from 'app/entities/tutor-participation';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { TextAssessmentComponent } from 'app/text-assessment/text-assessment.component';
import { TextSubmission, TextSubmissionService } from 'app/entities/text-submission';
import { TextExercise } from 'app/entities/text-exercise';
import { TextAssessmentEditorComponent } from 'app/text-assessment/text-assessment-editor/text-assessment-editor.component';
import { ResizableInstructionsComponent } from 'app/text-assessment/resizable-instructions/resizable-instructions.component';
import { TextAssessmentDetailComponent } from 'app/text-assessment/text-assessment-detail/text-assessment-detail.component';
import { ComplaintsForTutorComponent } from 'app/complaints-for-tutor';
import { DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import { MockAccountService } from '../../mocks/mock-account.service';
import { Subject } from 'rxjs';
import { TextAssessmentsService } from 'app/entities/text-assessments/text-assessments.service';
import { Location } from '@angular/common';
import { textAssessmentRoutes } from 'app/text-assessment/text-assessment.route';
import { TextAssessmentDashboardComponent } from 'app/text-assessment/text-assessment-dashboard/text-assessment-dashboard.component';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';

chai.use(sinonChai);
const expect = chai.expect;

describe('TextAssessmentComponent', () => {
    let comp: TextAssessmentComponent;
    let fixture: ComponentFixture<TextAssessmentComponent>;
    let textSubmissionService: TextSubmissionService;
    let route: ActivatedRoute;
    let textAssessmentsService: TextAssessmentsService;

    let getTextSubmissionForExerciseWithoutAssessmentStub: SinonStub;

    let getFeedbackDataForExerciseSubmissionStub: SinonStub;

    let debugElement: DebugElement;

    let router: Router;
    let location: Location;

    const exercise = { id: 20, type: ExerciseType.TEXT, tutorParticipations: [{ status: TutorParticipationStatus.TRAINED }] } as TextExercise;
    const submission = { id: 30 } as TextSubmission;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [ArTEMiSTestModule, ArTEMiSSharedModule, RouterTestingModule.withRoutes([textAssessmentRoutes[0]])],
            declarations: [
                TextAssessmentComponent,
                MockComponent(UpdatingResultComponent),
                MockComponent(TextAssessmentEditorComponent),
                MockComponent(ResizableInstructionsComponent),
                MockComponent(TextAssessmentDetailComponent),
                MockComponent(ComplaintsForTutorComponent),
                //TextAssessmentDashboardComponent,
            ],
            providers: [
                JhiLanguageHelper,
                { provide: JhiAlertService, useClass: MockAlertService },
                //{ provide: ActivatedRoute, useClass: MockActivatedRoute },
                { provide: AccountService, useClass: MockAccountService },
                //{ provide: Router, useClass: MockRouter },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TextAssessmentComponent);
                comp = fixture.componentInstance;
                comp.exercise = exercise;

                debugElement = fixture.debugElement;

                router = debugElement.injector.get(Router);
                location = debugElement.injector.get(Location);

                textSubmissionService = TestBed.get(TextSubmissionService);
                textAssessmentsService = TestBed.get(TextAssessmentsService);
                getTextSubmissionForExerciseWithoutAssessmentStub = stub(textSubmissionService, 'getTextSubmissionForExerciseWithoutAssessment');
                getFeedbackDataForExerciseSubmissionStub = stub(textAssessmentsService, 'getFeedbackDataForExerciseSubmission');

                router.initialNavigation();
            });
    });

    afterEach(() => {
        getTextSubmissionForExerciseWithoutAssessmentStub.restore();
        getFeedbackDataForExerciseSubmissionStub.restore();
    });

    it('AssessNextButton should be visible and the method assessNextOptimal should be invoked', fakeAsync(() => {
        // set all attributes for comp
        comp.ngOnInit();
        tick();

        comp.userId = 99;
        comp.submission = { submissionExerciseType: 'text', id: 2278, submitted: true, type: 'MANUAL', submissionDate: '2019-07-09T12:47:33.244+02:00', text: 'asdfasdfasdfasdf' };
        comp.result = {
            id: 2374,
            resultString: '1 of 12 points',
            completionDate: moment('2019-07-09T11:51:23.251Z'),
            successful: false,
            score: 8,
            rated: true,
            hasFeedback: false,
            submission: { submissionExerciseType: 'text', id: 2278, submitted: true, type: 'MANUAL', submissionDate: '2019-07-09T10:47:33.244Z', text: 'asdfasdfasdfasdf' },
            feedbacks: [
                { id: 11683, credits: 0, positive: true, type: 'MANUAL' },
                { id: 11684, detailText: 'asdfasdfasdf', reference: 'asdfasdfasdfasdf', credits: 1, positive: true, type: 'MANUAL' },
            ],
            participation: {
                id: 1396,
                initializationState: 'FINISHED',
                initializationDate: '2019-07-09T10:47:28.262Z',
                student: {
                    id: 17,
                    login: 'artemis_test_user_1',
                    firstName: 'ArTEMiS Test User 1',
                    email: 'krusche+testuser_1@in.tum.de',
                    activated: true,
                    langKey: 'en',
                    lastNotificationRead: '2019-06-21T20:45:33+02:00',
                    name: 'ArTEMiS Test User 1',
                },
                exercise: {
                    type: 'text',
                    id: 625,
                    title: 'text11',
                    releaseDate: '2019-07-09T12:45:57+02:00',
                    dueDate: '2019-07-09T12:48:01+02:00',
                    assessmentDueDate: '2019-07-16T12:48:01+02:00',
                    maxScore: 12,
                    problemStatement: 'asdfasdf',
                    gradingInstructions: 'asdfasdfsadf',
                    course: {
                        id: 4,
                        title: 'Francisco Test Course',
                        shortName: 'FTC',
                        studentGroupName: 'tumuser',
                        instructorGroupName: 'artemis-dev',
                        onlineCourse: false,
                        color: '#3E8ACC',
                        lectures: [
                            {
                                id: 19,
                                title: 'test lecture',
                                description: 'asdfasdf',
                                attachments: [
                                    {
                                        id: 17,
                                        name: 'LEcture PDF',
                                        link: 'POM19 10 Continuous Integration (1).pdf',
                                        version: 1,
                                        uploadDate: '2019-05-29T09:24:26+02:00',
                                        attachmentType: 'FILE',
                                    },
                                ],
                            },
                        ],
                    },
                    sampleSolution: 'asdfasdf',
                    ended: true,
                    visibleToStudents: true,
                },
            },
            assessor: {
                id: 8,
                login: 'ga36yih',
                firstName: 'Francisco De las Casas Young',
                email: 'f.de-las-casas-young@tum.de',
                activated: true,
                langKey: 'en',
                lastNotificationRead: '2019-04-13T15:55:50+02:00',
                name: 'Francisco De las Casas Young',
            },
            assessmentType: 'MANUAL',
        };
        comp.isAuthorized = true;
        comp.isAtLeastInstructor = true;
        comp.assessmentsAreValid = true;
        const unassessedSubmission = {
            submissionExerciseType: 'text',
            id: 2279,
            submitted: true,
            type: 'MANUAL',
            participation: {
                id: 1397,
                initializationState: 'FINISHED',
                initializationDate: '2019-07-09T12:47:52.152+02:00',
                submissions: [
                    { submissionExerciseType: 'text', id: 2279, submitted: true, type: 'MANUAL', submissionDate: '2019-07-09T12:47:57.287+02:00', text: 'asdfasdfa sfasdfasdf ' },
                ],
                student: {
                    id: 37,
                    login: 'artemis_test_user_2',
                    firstName: 'ArTEMiS Test User 2',
                    email: 'krusche+testuser_2@in.tum.de',
                    activated: true,
                    langKey: 'en',
                    lastNotificationRead: '2019-05-27T20:10:24+02:00',
                    name: 'ArTEMiS Test User 2',
                },
                exercise: {
                    type: 'text',
                    id: 625,
                    title: 'text11',
                    releaseDate: '2019-07-09T12:45:57+02:00',
                    dueDate: '2019-07-09T12:48:01+02:00',
                    assessmentDueDate: '2019-07-16T12:48:01+02:00',
                    maxScore: 12,
                    problemStatement: 'asdfasdf',
                    gradingInstructions: 'asdfasdfsadf',
                    course: {
                        id: 4,
                        title: 'Francisco Test Course',
                        shortName: 'FTC',
                        studentGroupName: 'tumuser',
                        instructorGroupName: 'artemis-dev',
                        onlineCourse: false,
                        color: '#3E8ACC',
                        lectures: [
                            {
                                id: 19,
                                title: 'test lecture',
                                description: 'asdfasdf',
                                attachments: [
                                    {
                                        id: 17,
                                        name: 'LEcture PDF',
                                        link: 'POM19 10 Continuous Integration (1).pdf',
                                        version: 1,
                                        uploadDate: '2019-05-29T09:24:26+02:00',
                                        attachmentType: 'FILE',
                                    },
                                ],
                            },
                        ],
                    },
                    sampleSolution: 'asdfasdf',
                    ended: true,
                    visibleToStudents: true,
                },
            },
            submissionDate: '2019-07-09T12:47:57.287+02:00',
            text: 'asdfasdfa sfasdfasdf ',
        };

        fixture.detectChanges();

        const assessNextButton = debugElement.query(By.css('#assessNextButton'));
        expect(assessNextButton).to.exist;

        getTextSubmissionForExerciseWithoutAssessmentStub.returns(of(unassessedSubmission));
        assessNextButton.nativeElement.click();
        expect(getTextSubmissionForExerciseWithoutAssessmentStub).to.have.been.called;
        expect(comp.unassessedSubmission).to.be.deep.equal(unassessedSubmission);

        //next test:
        //router.navigate(['text/:exerciseId/assessment/:submissionId']);
        tick();
        expect(location.path()).to.be.equal('/text/' + comp.exercise.id + '/assessment/' + comp.unassessedSubmission.id);

        //getFeedbackDataForExerciseSubmissionStub.returns(of());
        //expect(getFeedbackDataForExerciseSubmissionStub).to.have.been.called;

        fixture.destroy();
        flush();
    }));
});
