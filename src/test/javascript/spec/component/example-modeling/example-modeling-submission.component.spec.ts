import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { delay, of, throwError } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { ActivatedRoute, ActivatedRouteSnapshot, Router, convertToParamMap } from '@angular/router';
import { ChangeDetectorRef, DebugElement } from '@angular/core';
import { MockComponent, MockModule, MockProvider } from 'ng-mocks';
import { ModelingEditorComponent } from 'app/exercises/modeling/shared/modeling-editor.component';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Result } from 'app/entities/result.model';
import { Feedback, FeedbackCorrectionError, FeedbackCorrectionErrorType, FeedbackType } from 'app/entities/feedback.model';
import { UMLModel } from '@ls1intum/apollon';
import { HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { ExampleModelingSubmissionComponent } from 'app/exercises/modeling/manage/example-modeling/example-modeling-submission.component';
import { ExampleSubmissionService } from 'app/exercises/shared/example-submission/example-submission.service';
import { ExampleSubmission } from 'app/entities/example-submission.model';
import { ArtemisExampleModelingSubmissionRoutingModule } from 'app/exercises/modeling/manage/example-modeling/example-modeling-submission.route';
import { TutorParticipationService } from 'app/exercises/shared/dashboards/tutor/tutor-participation.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ModelingAssessmentService } from 'app/exercises/modeling/assess/modeling-assessment.service';
import { MockTranslateService, TranslateTestingModule } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateValuesDirective } from '../../helpers/mocks/directive/mock-translate-values.directive';
import { FaLayersComponent } from '@fortawesome/angular-fontawesome';
import { CollapsableAssessmentInstructionsComponent } from 'app/assessment/assessment-instructions/collapsable-assessment-instructions/collapsable-assessment-instructions.component';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { ModelingAssessmentComponent } from 'app/exercises/modeling/assess/modeling-assessment.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { UnreferencedFeedbackComponent } from 'app/exercises/shared/unreferenced-feedback/unreferenced-feedback.component';
import { ScoreDisplayComponent } from 'app/shared/score-display/score-display.component';
import { FormsModule } from '@angular/forms';

describe('Example Modeling Submission Component', () => {
    let comp: ExampleModelingSubmissionComponent;
    let fixture: ComponentFixture<ExampleModelingSubmissionComponent>;
    let debugElement: DebugElement;
    let service: ExampleSubmissionService;
    let alertService: AlertService;
    let router: Router;
    let route: ActivatedRoute;

    const participation = new StudentParticipation();
    participation.exercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined);
    participation.id = 1;
    const submission = { id: 20, submitted: true, participation } as ModelingSubmission;

    const exampleSubmission: ExampleSubmission = {
        submission,
    };

    const exercise = {
        id: 22,
        diagramType: UMLDiagramType.ClassDiagram,
        course: { id: 2 },
    } as ModelingExercise;

    const mockFeedbackWithReference = {
        text: 'FeedbackWithReference',
        referenceId: 'relationshipId',
        reference: 'reference',
        credits: 30,
        correctionStatus: 'CORRECT',
    } as Feedback;
    const mockFeedbackWithoutReference = { text: 'FeedbackWithoutReference', credits: 30, type: FeedbackType.MANUAL_UNREFERENCED } as Feedback;
    const mockFeedbackInvalid = { text: 'FeedbackInvalid', referenceId: '4', reference: 'reference', correctionStatus: FeedbackCorrectionErrorType.INCORRECT_SCORE };
    const mockFeedbackCorrectionError = { reference: 'reference', type: FeedbackCorrectionErrorType.INCORRECT_SCORE } as FeedbackCorrectionError;

    beforeEach(() => {
        route = {
            snapshot: {
                paramMap: convertToParamMap({ exerciseId: '22', exampleSubmissionId: '35' }),
                queryParamMap: convertToParamMap({ readOnly: 0, toComplete: 0 }),
            },
        } as ActivatedRoute;

        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(ArtemisExampleModelingSubmissionRoutingModule), TranslateTestingModule, FormsModule],
            declarations: [
                ExampleModelingSubmissionComponent,
                ModelingAssessmentComponent,
                MockComponent(ModelingEditorComponent),
                MockTranslateValuesDirective,
                MockComponent(FaLayersComponent),
                MockComponent(CollapsableAssessmentInstructionsComponent),
                MockComponent(UnreferencedFeedbackComponent),
                MockComponent(ScoreDisplayComponent),
            ],
            providers: [
                MockProvider(ChangeDetectorRef),
                MockProvider(ArtemisTranslatePipe),
                { provide: Router, useClass: MockRouter },
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExampleModelingSubmissionComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                service = debugElement.injector.get(ExampleSubmissionService);
                alertService = debugElement.injector.get(AlertService);
                router = debugElement.injector.get(Router);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        // GIVEN
        jest.spyOn(service, 'get').mockReturnValue(of(new HttpResponse({ body: exampleSubmission })));
        const exerciseService = debugElement.injector.get(ExerciseService);
        jest.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: exercise })));

        // WHEN
        fixture.detectChanges();

        // THEN
        expect(comp).toBe(comp);
    });

    it('should handle a new submission', () => {
        route.snapshot = { ...route.snapshot, paramMap: convertToParamMap({ exerciseId: '22', exampleSubmissionId: 'new' }) } as ActivatedRouteSnapshot;

        // WHEN
        fixture.detectChanges();

        // THEN
        expect(comp.isNewSubmission).toBeTrue();
        expect(comp.exampleSubmission).toEqual(new ExampleSubmission());
    });

    it('should upsert a new modeling submission', () => {
        // GIVEN
        const alertSpy = jest.spyOn(alertService, 'success');
        const serviceSpy = jest.spyOn(service, 'create').mockImplementation((newExampleSubmission) => of(new HttpResponse({ body: newExampleSubmission })));
        comp.isNewSubmission = true;
        comp.exercise = exercise;
        // WHEN
        fixture.detectChanges(); // Needed for @ViewChild to set fields.
        comp.upsertExampleModelingSubmission();

        // THEN
        expect(comp.isNewSubmission).toBeFalse();
        expect(serviceSpy).toHaveBeenCalledOnce();

        expect(alertSpy).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenCalledWith('artemisApp.modelingEditor.saveSuccessful');
    });

    it('should upsert an existing modeling submission', fakeAsync(() => {
        // GIVEN
        jest.spyOn(service, 'get').mockReturnValue(of(new HttpResponse({ body: exampleSubmission })));
        const alertSpy = jest.spyOn(alertService, 'success');
        const serviceSpy = jest.spyOn(service, 'update').mockImplementation((updatedExampleSubmission) => of(new HttpResponse({ body: updatedExampleSubmission })).pipe(delay(1)));

        const modelingAssessmentService = debugElement.injector.get(ModelingAssessmentService);
        const modelingAssessmentServiceSpy = jest.spyOn(modelingAssessmentService, 'saveExampleAssessment');

        comp.isNewSubmission = false;
        comp.exercise = exercise;
        comp.exampleSubmission = exampleSubmission;

        // WHEN
        fixture.detectChanges();
        comp.upsertExampleModelingSubmission();

        // Ensure calls are not concurrent, new one should start after first one ends.
        expect(serviceSpy).toHaveBeenCalledOnce();
        tick(1);

        // This service request should also start after the previous one ends.
        expect(modelingAssessmentServiceSpy).not.toHaveBeenCalled();
        tick(1);

        // THEN
        expect(comp.isNewSubmission).toBeFalse();
        expect(serviceSpy).toHaveBeenCalledTimes(2);
        expect(modelingAssessmentServiceSpy).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenCalledWith('artemisApp.modelingEditor.saveSuccessful');
    }));

    it('should check assessment', () => {
        // GIVEN
        const tutorParticipationService = debugElement.injector.get(TutorParticipationService);
        const assessExampleSubmissionSpy = jest.spyOn(tutorParticipationService, 'assessExampleSubmission');
        const exerciseId = 5;
        comp.exampleSubmission = exampleSubmission;
        comp.exerciseId = exerciseId;

        // WHEN
        comp.checkAssessment();

        // THEN
        expect(comp.assessmentsAreValid).toBeTrue();
        expect(assessExampleSubmissionSpy).toHaveBeenCalledOnce();
        expect(assessExampleSubmissionSpy).toHaveBeenCalledWith(exampleSubmission, exerciseId);
    });

    it('should check invalid assessment', () => {
        // GIVEN
        const alertSpy = jest.spyOn(alertService, 'error');
        comp.exampleSubmission = exampleSubmission;

        // WHEN
        comp.onReferencedFeedbackChanged([mockFeedbackInvalid]);
        comp.checkAssessment();

        // THEN
        expect(alertSpy).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenCalledWith('artemisApp.modelingAssessment.invalidAssessments');
    });

    it('should read and understood', () => {
        // GIVEN
        const tutorParticipationService = debugElement.injector.get(TutorParticipationService);
        jest.spyOn(tutorParticipationService, 'assessExampleSubmission').mockReturnValue(of(new HttpResponse({ body: {} })));
        const alertSpy = jest.spyOn(alertService, 'success');
        const routerSpy = jest.spyOn(router, 'navigate');
        comp.exercise = exercise;
        comp.exampleSubmission = exampleSubmission;

        // WHEN
        fixture.detectChanges();
        comp.readAndUnderstood();

        // THEN
        expect(alertSpy).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenCalledWith('artemisApp.exampleSubmission.readSuccessfully');
        expect(routerSpy).toHaveBeenCalledOnce();
    });

    it('should handle referenced feedback change', () => {
        // GIVEN
        const feedbacks = [mockFeedbackWithReference];
        comp.exercise = exercise;

        // WHEN
        comp.onReferencedFeedbackChanged(feedbacks);

        // THEN
        expect(comp.feedbackChanged).toBeTrue();
        expect(comp.assessmentsAreValid).toBeTrue();
        expect(comp.referencedFeedback).toEqual(feedbacks);
    });

    it('should handle unreferenced feedback change', () => {
        // GIVEN
        const feedbacks = [mockFeedbackWithoutReference];
        comp.exercise = exercise;

        // WHEN
        comp.onUnReferencedFeedbackChanged(feedbacks);

        // THEN
        expect(comp.feedbackChanged).toBeTrue();
        expect(comp.assessmentsAreValid).toBeTrue();
        expect(comp.unreferencedFeedback).toEqual(feedbacks);
    });

    it('should show submission', () => {
        // GIVEN
        const feedbacks = [mockFeedbackWithReference];
        comp.exercise = exercise;
        comp.exampleSubmission = exampleSubmission;

        // WHEN
        comp.onReferencedFeedbackChanged(feedbacks);
        comp.showSubmission();

        // THEN
        expect(comp.feedbackChanged).toBeFalse();
        expect(comp.assessmentMode).toBeFalse();
        expect(comp.totalScore).toBe(mockFeedbackWithReference.credits);
    });

    it('should create error alert if assessment is invalid', () => {
        // GIVEN
        const alertSpy = jest.spyOn(alertService, 'error');
        comp.exercise = exercise;
        comp.exampleSubmission = exampleSubmission;
        comp.referencedFeedback = [mockFeedbackInvalid];

        // WHEN
        comp.saveExampleAssessment();

        // THEN
        expect(alertSpy).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenCalledWith('artemisApp.modelingAssessment.invalidAssessments');
    });

    it('should update assessment explanation and example assessment', () => {
        // GIVEN
        comp.exercise = exercise;
        comp.exampleSubmission = { ...exampleSubmission, assessmentExplanation: 'Explanation of the assessment' };
        comp.referencedFeedback = [mockFeedbackWithReference];
        comp.unreferencedFeedback = [mockFeedbackWithoutReference];

        const result = { id: 1 } as Result;
        const alertSpy = jest.spyOn(alertService, 'success');
        jest.spyOn(service, 'update').mockImplementation((updatedExampleSubmission) => of(new HttpResponse({ body: updatedExampleSubmission })));
        const modelingAssessmentService = debugElement.injector.get(ModelingAssessmentService);
        jest.spyOn(modelingAssessmentService, 'saveExampleAssessment').mockReturnValue(of(result));

        // WHEN
        comp.saveExampleAssessment();

        // THEN
        expect(comp.result).toBe(result);
        expect(alertSpy).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenCalledWith('artemisApp.modelingAssessmentEditor.messages.saveSuccessful');
    });

    it('should update assessment explanation but create error message on example assessment update failure', () => {
        // GIVEN
        comp.exercise = exercise;
        comp.exampleSubmission = { ...exampleSubmission, assessmentExplanation: 'Explanation of the assessment' };
        comp.referencedFeedback = [mockFeedbackWithReference, mockFeedbackWithoutReference];

        const alertSpy = jest.spyOn(alertService, 'error');
        jest.spyOn(service, 'update').mockImplementation((updatedExampleSubmission) => of(new HttpResponse({ body: updatedExampleSubmission })));
        const modelingAssessmentService = debugElement.injector.get(ModelingAssessmentService);
        jest.spyOn(modelingAssessmentService, 'saveExampleAssessment').mockReturnValue(throwError(() => ({ status: 404 })));

        // WHEN
        comp.saveExampleAssessment();

        // THEN
        expect(comp.result).toBeUndefined();
        expect(alertSpy).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenCalledWith('artemisApp.modelingAssessmentEditor.messages.saveFailed');
    });

    it('should mark all feedback correct', () => {
        // GIVEN
        comp.exercise = exercise;
        comp.exampleSubmission = exampleSubmission;
        comp.referencedFeedback = [mockFeedbackInvalid];
        comp.assessmentMode = true;

        // WHEN
        comp.showAssessment();
        fixture.detectChanges();
        const resultFeedbacksSetterSpy = jest.spyOn(comp.assessmentEditor, 'resultFeedbacks', 'set');

        comp.markAllFeedbackToCorrect();
        fixture.detectChanges();

        // THEN
        expect(comp.referencedFeedback.every((feedback) => feedback.correctionStatus === 'CORRECT')).toBeTrue();
        expect(resultFeedbacksSetterSpy).toHaveBeenCalledOnce();
        expect(resultFeedbacksSetterSpy).toHaveBeenCalledWith(comp.referencedFeedback);
    });

    it('should mark all feedback wrong', () => {
        // GIVEN
        comp.exercise = exercise;
        comp.exampleSubmission = exampleSubmission;
        comp.referencedFeedback = [mockFeedbackInvalid];
        comp.assessmentMode = true;

        // WHEN
        comp.showAssessment();
        fixture.detectChanges();
        const resultFeedbacksSetterSpy = jest.spyOn(comp.assessmentEditor, 'resultFeedbacks', 'set');

        comp.markWrongFeedback([mockFeedbackCorrectionError]);
        fixture.detectChanges();

        // THEN
        expect(comp.referencedFeedback[0].correctionStatus).toBe(mockFeedbackCorrectionError.type);
        expect(resultFeedbacksSetterSpy).toHaveBeenCalledOnce();
        expect(resultFeedbacksSetterSpy).toHaveBeenCalledWith(comp.referencedFeedback);
    });

    it('should create success alert on example assessment update', () => {
        // GIVEN
        const result = { id: 1 } as Result;
        const modelingAssessmentService = debugElement.injector.get(ModelingAssessmentService);
        jest.spyOn(modelingAssessmentService, 'saveExampleAssessment').mockReturnValue(of(result));
        const alertSpy = jest.spyOn(alertService, 'success');

        comp.exercise = exercise;
        comp.exampleSubmission = exampleSubmission;
        comp.referencedFeedback = [mockFeedbackWithReference];

        // WHEN
        comp.saveExampleAssessment();

        // THEN
        comp.result = result;
        expect(alertSpy).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenCalledWith('artemisApp.modelingAssessmentEditor.messages.saveSuccessful');
    });

    it('should create error alert on example assessment update failure', () => {
        // GIVEN
        const modelingAssessmentService = debugElement.injector.get(ModelingAssessmentService);
        jest.spyOn(modelingAssessmentService, 'saveExampleAssessment').mockReturnValue(throwError(() => ({ status: 404 })));
        const alertSpy = jest.spyOn(alertService, 'error');

        comp.exercise = exercise;
        comp.exampleSubmission = exampleSubmission;
        comp.referencedFeedback = [mockFeedbackWithReference];

        // WHEN
        comp.saveExampleAssessment();

        // THEN
        expect(alertSpy).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenCalledWith('artemisApp.modelingAssessmentEditor.messages.saveFailed');
    });

    it('should handle explanation change', () => {
        // GIVEN
        const explanation = 'New Explanation';

        // WHEN
        comp.explanationChanged(explanation);

        // THEN
        expect(comp.explanationText).toBe(explanation);
    });

    it('should show assessment', () => {
        // GIVEN
        const model = {
            version: '2.0.0',
            type: 'ClassDiagram',
        } as UMLModel;

        const result = { id: 1 } as Result;

        jest.spyOn(service, 'get').mockReturnValue(of(new HttpResponse({ body: exampleSubmission })));
        const modelingAssessmentService = debugElement.injector.get(ModelingAssessmentService);
        const assessmentSpy = jest.spyOn(modelingAssessmentService, 'getExampleAssessment').mockReturnValue(of(result));

        comp.exercise = exercise;
        comp.exampleSubmission = exampleSubmission;

        // WHEN
        fixture.detectChanges();
        jest.spyOn(comp.modelingEditor, 'getCurrentModel').mockReturnValue(model);

        comp.showAssessment();

        // THEN
        expect(assessmentSpy).toHaveBeenCalledOnce();
        expect(comp.assessmentMode).toBeTrue();
        expect(result.feedbacks).toEqual(comp.assessments);
    });
});
