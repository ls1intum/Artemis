import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { TranslateService } from '@ngx-translate/core';
import { AssessmentLayoutComponent } from 'app/assessment/assessment-layout/assessment-layout.component';
import { ComplaintService, EntityResponseType } from 'app/complaints/complaint.service';
import { AccountService } from 'app/core/auth/account.service';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { User } from 'app/core/user/user.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { Complaint } from 'app/entities/complaint.model';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { Exercise } from 'app/entities/exercise.model';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { Participation, ParticipationType } from 'app/entities/participation/participation.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { Result } from 'app/entities/result.model';
import { getLatestSubmissionResult } from 'app/entities/submission.model';
import { ModelingAssessmentEditorComponent } from 'app/exercises/modeling/assess/modeling-assessment-editor/modeling-assessment-editor.component';
import { ModelingAssessmentService } from 'app/exercises/modeling/assess/modeling-assessment.service';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of, throwError } from 'rxjs';
import { mockedActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route-query-param-map';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../test.module';
import { SubmissionService } from 'app/exercises/shared/submission/submission.service';
import { MockComponent } from 'ng-mocks';
import { ModelingAssessmentComponent } from 'app/exercises/modeling/assess/modeling-assessment.component';
import { CollapsableAssessmentInstructionsComponent } from 'app/assessment/assessment-instructions/collapsable-assessment-instructions/collapsable-assessment-instructions.component';
import { UnreferencedFeedbackComponent } from 'app/exercises/shared/unreferenced-feedback/unreferenced-feedback.component';
import { ExampleSubmissionService } from 'app/exercises/shared/example-submission/example-submission.service';
import { ExampleSubmission } from 'app/entities/example-submission.model';
import dayjs from 'dayjs/esm';

describe('ModelingAssessmentEditorComponent', () => {
    let component: ModelingAssessmentEditorComponent;
    let fixture: ComponentFixture<ModelingAssessmentEditorComponent>;
    let service: ModelingAssessmentService;
    let mockAuth: MockAccountService;
    let modelingSubmissionService: ModelingSubmissionService;
    let complaintService: ComplaintService;
    let modelingSubmissionSpy: jest.SpyInstance;
    let complaintSpy: jest.SpyInstance;
    let router: any;
    let submissionService: SubmissionService;
    let exampleSubmissionService: ExampleSubmissionService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule],
            declarations: [
                ModelingAssessmentEditorComponent,
                MockComponent(AssessmentLayoutComponent),
                MockComponent(ModelingAssessmentComponent),
                MockComponent(CollapsableAssessmentInstructionsComponent),
                MockComponent(UnreferencedFeedbackComponent),
            ],
            providers: [
                JhiLanguageHelper,
                mockedActivatedRoute({}, { showBackButton: 'false', submissionId: 'new', exerciseId: 1 }),
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ModelingAssessmentEditorComponent);
                component = fixture.componentInstance;
                service = TestBed.inject(ModelingAssessmentService);
                modelingSubmissionService = TestBed.inject(ModelingSubmissionService);
                complaintService = TestBed.inject(ComplaintService);
                router = TestBed.inject(Router);
                submissionService = TestBed.inject(SubmissionService);
                mockAuth = fixture.debugElement.injector.get(AccountService) as any as MockAccountService;
                exampleSubmissionService = TestBed.inject(ExampleSubmissionService);
                mockAuth.hasAnyAuthorityDirect([]);
                mockAuth.identity();
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });
    describe('ngOnInit tests', () => {
        it('ngOnInit', fakeAsync(() => {
            modelingSubmissionSpy = jest.spyOn(modelingSubmissionService, 'getSubmission');
            complaintSpy = jest.spyOn(complaintService, 'findBySubmissionId');
            const submission = {
                id: 1,
                submitted: true,
                type: 'MANUAL',
                text: 'Test\n\nTest\n\nTest',
                participation: {
                    type: ParticipationType.SOLUTION,
                    exercise: {
                        id: 1,
                        problemStatement: 'problem',
                        gradingInstructions: 'grading',
                        title: 'title',
                        shortName: 'name',
                        exerciseGroup: {
                            exam: {
                                course: new Course(),
                            } as unknown as Exam,
                        } as unknown as ExerciseGroup,
                    } as unknown as Exercise,
                } as unknown as Participation,
                results: [
                    {
                        id: 2374,
                        resultString: '1 of 12 points',
                        score: 8,
                        rated: true,
                        hasFeedback: true,
                        hasComplaint: true,
                        feedbacks: [
                            {
                                id: 2,
                                detailText: 'Feedback',
                                credits: 1,
                            } as Feedback,
                        ],
                    } as unknown as Result,
                ],
            } as unknown as ModelingSubmission;

            modelingSubmissionSpy.mockReturnValue(of(submission));
            const user = <User>{ id: 99, groups: ['instructorGroup'] };
            const result: Result = {
                feedbacks: [new Feedback()],
                participation: new StudentParticipation(),
                score: 80,
                successful: true,
                submission: new ProgrammingSubmission(),
                assessor: user,
                hasComplaint: true,
                assessmentType: AssessmentType.SEMI_AUTOMATIC,
                id: 2,
            };
            const complaint = <Complaint>{ id: 1, complaintText: 'Why only 80%?', result };
            complaintSpy.mockReturnValue(of({ body: complaint } as HttpResponse<Complaint>));

            const handleFeedbackSpy = jest.spyOn(submissionService, 'handleFeedbackCorrectionRoundTag');

            component.ngOnInit();
            tick(500);
            expect(modelingSubmissionSpy).toHaveBeenCalledOnce();
            expect(component.isLoading).toBeFalse();
            expect(component.complaint).toEqual(complaint);
            modelingSubmissionSpy.mockRestore();
            expect(handleFeedbackSpy).toHaveBeenCalled();
        }));

        it('wrongly call ngOnInit and throw exception', fakeAsync(() => {
            modelingSubmissionSpy = jest.spyOn(modelingSubmissionService, 'getSubmission');
            const response = new HttpErrorResponse({ status: 403 });
            modelingSubmissionSpy.mockReturnValue(throwError(() => response));

            component.ngOnInit();
            tick(500);
            expect(modelingSubmissionSpy).toHaveBeenCalledOnce();
            modelingSubmissionSpy.mockRestore();
        }));
    });

    describe('should test the overwrite access rights and return true', () => {
        it('tests the method with instructor rights', fakeAsync(() => {
            const course = new Course();
            component.ngOnInit();
            tick(500);
            component.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
            component.modelingExercise.isAtLeastInstructor = true;
            expect(component.canOverride).toBeTrue();
        }));

        it('tests the method with tutor rights and as assessor', fakeAsync(() => {
            const course = new Course();
            component.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
            component.modelingExercise.isAtLeastInstructor = false;
            component.isAssessor = true;
            component.complaint = new Complaint();
            component.complaint.id = 0;
            component.complaint.complaintText = 'complaint';
            component.ngOnInit();
            tick(500);
            mockAuth.isAtLeastInstructorInCourse(course);
            component['checkPermissions']();
            fixture.detectChanges();
            expect(component.modelingExercise.isAtLeastInstructor).toBeFalse();
            expect(component.canOverride).toBeFalse();
        }));
    });

    it('should save assessment', fakeAsync(() => {
        const course = new Course();
        component.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
        component.modelingExercise.maxPoints = 10;

        const feedback = createTestFeedback();
        component.unreferencedFeedback = [feedback];

        component.result = {
            id: 2374,
            resultString: '1 of 12 points',
            score: 8,
            rated: true,
            hasFeedback: true,
            hasComplaint: false,
        } as unknown as Result;

        component.submission = {
            id: 1,
            submitted: true,
            type: 'MANUAL',
            text: 'Test\n\nTest\n\nTest',
        } as unknown as ModelingSubmission;
        component.submission.results = [component.result];
        getLatestSubmissionResult(component.submission)!.feedbacks = [
            {
                id: 2,
                detailText: 'Feedback',
                credits: 1,
            } as Feedback,
        ];
        const saveAssessmentSpy = jest.spyOn(service, 'saveAssessment').mockReturnValue(of(getLatestSubmissionResult(component.submission)!));

        component.ngOnInit();
        tick(500);
        component.onSaveAssessment();
        expect(saveAssessmentSpy).toHaveBeenCalledOnce();
    }));

    it('should try to submit assessment', fakeAsync(() => {
        const course = new Course();
        component.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
        component.modelingExercise.assessmentDueDate = dayjs().subtract(2, 'days');

        // make sure feedback is valid
        const feedback = createTestFeedback();
        component.unreferencedFeedback = [feedback];

        component.submission = {
            id: 1,
            submitted: true,
            type: 'MANUAL',
            text: 'Test\n\nTest\n\nTest',
        } as unknown as ModelingSubmission;
        component.submission.results = [
            {
                id: 2374,
                resultString: '1 of 12 points',
                score: 8,
                rated: true,
                hasFeedback: true,
                hasComplaint: false,
            } as unknown as Result,
        ];
        getLatestSubmissionResult(component.submission)!.feedbacks = [
            {
                id: 2,
                detailText: 'Feedback',
                credits: 1,
            } as Feedback,
        ];
        jest.spyOn(service, 'saveAssessment').mockReturnValue(of(getLatestSubmissionResult(component.submission)!));
        jest.spyOn(window, 'confirm').mockReturnValue(false);

        component.ngOnInit();
        tick(500);

        component.onSubmitAssessment();

        expect(window.confirm).toHaveBeenCalledOnce();
        expect(component.highlightMissingFeedback).toBeTrue();
    }));

    const createTestFeedback = (): Feedback => {
        const feedback = new Feedback();
        feedback.id = 2;
        feedback.text = 'This is a test feedback';
        feedback.detailText = 'Feedback';
        feedback.credits = 1;
        feedback.type = FeedbackType.MANUAL_UNREFERENCED;
        return feedback;
    };

    it('should update assessment after complaint', fakeAsync(() => {
        const complaintResponse = new ComplaintResponse();
        complaintResponse.id = 1;
        complaintResponse.responseText = 'response';

        component.submission = {
            id: 1,
            submitted: true,
            type: 'MANUAL',
            text: 'Test\n\nTest\n\nTest',
        } as unknown as ModelingSubmission;

        const changedResult = {
            id: 2374,
            resultString: '1 of 12 points',
            score: 8,
            rated: true,
            hasFeedback: true,
            hasComplaint: false,
            participation: {
                type: ParticipationType.SOLUTION,
                results: [],
            } as unknown as Participation,
        } as unknown as Result;

        const serviceSpy = jest.spyOn(service, 'updateAssessmentAfterComplaint').mockReturnValue(of({ body: changedResult } as EntityResponseType));

        component.ngOnInit();
        tick(500);

        component.onUpdateAssessmentAfterComplaint(complaintResponse);
        expect(serviceSpy).toHaveBeenCalledOnce();
        expect(component.result?.participation?.results).toEqual([changedResult]);
    }));

    it('should cancel the current assessment', fakeAsync(() => {
        const windowSpy = jest.spyOn(window, 'confirm').mockReturnValue(true);

        component.submission = {
            id: 2,
            submitted: true,
            type: 'MANUAL',
            text: 'Test\n\nTest\n\nTest',
        } as unknown as ModelingSubmission;

        const serviceSpy = jest.spyOn(service, 'cancelAssessment').mockReturnValue(of());

        component.ngOnInit();
        tick(500);

        component.onCancelAssessment();
        expect(windowSpy).toHaveBeenCalledOnce();
        expect(serviceSpy).toHaveBeenCalledOnce();
    }));

    it('should handle changed feedback', fakeAsync(() => {
        const feedbacks = [
            {
                id: 0,
                credits: 3,
                reference: 'reference',
            } as Feedback,
            {
                id: 1,
                credits: 1,
            } as Feedback,
        ];

        component.ngOnInit();
        tick(500);

        const course = new Course();
        component.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
        component.modelingExercise.maxPoints = 5;
        component.modelingExercise.bonusPoints = 5;
        const handleFeedbackSpy = jest.spyOn(submissionService, 'handleFeedbackCorrectionRoundTag');
        component.onFeedbackChanged(feedbacks);
        expect(component.referencedFeedback).toHaveLength(1);
        expect(component.totalScore).toBe(3);
        expect(handleFeedbackSpy).toHaveBeenCalledOnce();
    }));

    describe('test assessNext', () => {
        it('no submissions left', fakeAsync(() => {
            const course = new Course();
            component.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
            component.modelingExercise.id = 1;

            const routerSpy = jest.spyOn(router, 'navigate').mockImplementation();
            const modelingSubmission: ModelingSubmission = { id: 1 };
            const serviceSpy = jest.spyOn(modelingSubmissionService, 'getModelingSubmissionForExerciseForCorrectionRoundWithoutAssessment').mockReturnValue(of(modelingSubmission));

            component.ngOnInit();

            const correctionRound = 1;
            const courseId = 1;
            const exerciseId = 1;
            component.correctionRound = correctionRound;
            component.courseId = courseId;
            component.modelingExercise = { id: exerciseId } as Exercise;
            component.exerciseId = exerciseId;
            const url = ['/course-management', courseId.toString(), 'modeling-exercises', exerciseId.toString(), 'submissions', modelingSubmission.id!.toString(), 'assessment'];
            const queryParams = { queryParams: { 'correction-round': correctionRound } };

            tick(500);
            component.assessNext();
            tick(500);

            expect(serviceSpy).toHaveBeenCalledOnce();
            expect(routerSpy).toHaveBeenCalledWith(url, queryParams);
        }));

        it('throw error while assessNext', fakeAsync(() => {
            const course = new Course();
            component.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
            component.modelingExercise.id = 1;

            const response = new HttpErrorResponse({ status: 403 });
            const serviceSpy = jest
                .spyOn(modelingSubmissionService, 'getModelingSubmissionForExerciseForCorrectionRoundWithoutAssessment')
                .mockReturnValue(throwError(() => response));

            component.ngOnInit();
            tick(500);
            component.assessNext();
            expect(serviceSpy).toHaveBeenCalledOnce();
        }));
    });

    it('should invoke import example submission', () => {
        const course = new Course();
        component.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
        component.modelingExercise.id = 1;
        component.submission = {
            id: 2,
            submitted: true,
            type: 'MANUAL',
            text: 'Test\n\nTest\n\nTest',
        } as ModelingSubmission;

        const importSpy = jest.spyOn(exampleSubmissionService, 'import').mockReturnValue(of(new HttpResponse({ body: new ExampleSubmission() })));

        component.useStudentSubmissionAsExampleSubmission();

        expect(importSpy).toHaveBeenCalledOnce();
        expect(importSpy).toHaveBeenCalledWith(component.submission.id, component.modelingExercise!.id);
    });
});
