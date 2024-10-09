import { DebugElement } from '@angular/core';
import dayjs from 'dayjs/esm';
import { ActivatedRoute, RouterModule, convertToParamMap } from '@angular/router';
import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { AlertService } from 'app/core/util/alert.service';
import { ArtemisTestModule } from '../../test.module';
import { TranslateService } from '@ngx-translate/core';
import { MockTextEditorService } from '../../helpers/mocks/service/mock-text-editor.service';
import { TextEditorService } from 'app/exercises/text/participate/text-editor.service';
import { BehaviorSubject } from 'rxjs';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { TextResultComponent } from 'app/exercises/text/participate/text-result/text-result.component';
import { SubmissionResultStatusComponent } from 'app/overview/submission-result-status.component';
import { TextEditorComponent } from 'app/exercises/text/participate/text-editor.component';
import { textEditorRoute } from 'app/exercises/text/participate/text-editor.route';
import { TextExercise } from 'app/entities/text/text-exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ButtonComponent } from 'app/shared/components/button.component';
import { Result } from 'app/entities/result.model';
import { ComplaintsFormComponent } from 'app/complaints/form/complaints-form.component';
import { TextSubmission } from 'app/entities/text/text-submission.model';
import { TextSubmissionService } from 'app/exercises/text/participate/text-submission.service';
import { MockTextSubmissionService } from '../../helpers/mocks/service/mock-text-submission.service';
import { Language } from 'app/entities/course.model';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { Participation } from 'app/entities/participation/participation.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Submission } from 'app/entities/submission.model';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { HeaderParticipationPageComponent } from 'app/exercises/shared/exercise-headers/header-participation-page.component';
import { ResizeableContainerComponent } from 'app/shared/resizeable-container/resizeable-container.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TeamParticipateInfoBoxComponent } from 'app/exercises/shared/team/team-participate/team-participate-info-box.component';
import { TeamSubmissionSyncComponent } from 'app/exercises/shared/team-submission-sync/team-submission-sync.component';
import { AdditionalFeedbackComponent } from 'app/shared/additional-feedback/additional-feedback.component';
import { RatingComponent } from 'app/exercises/shared/rating/rating.component';
import { NgModel } from '@angular/forms';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ComplaintsStudentViewComponent } from 'app/complaints/complaints-for-students/complaints-student-view.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { By } from '@angular/platform-browser';
import { MockCourseExerciseService } from '../../helpers/mocks/service/mock-course-exercise.service';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';

describe('TextEditorComponent', () => {
    let comp: TextEditorComponent;
    let fixture: ComponentFixture<TextEditorComponent>;
    let debugElement: DebugElement;
    let textService: TextEditorService;
    let textSubmissionService: TextSubmissionService;
    let getTextForParticipationStub: jest.SpyInstance;
    let courseExerciseService: CourseExerciseService;

    const route = { snapshot: { paramMap: convertToParamMap({ participationId: 42 }) } } as ActivatedRoute;
    const textExercise = { id: 1 } as TextExercise;
    const participation = new StudentParticipation();
    const result = new Result();

    beforeAll(() => {
        participation.id = 42;
        participation.exercise = textExercise;
        participation.submissions = [new TextSubmission()];
        result.id = 1;
    });

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterModule.forRoot([textEditorRoute[0]])],
            declarations: [
                TextEditorComponent,
                MockComponent(SubmissionResultStatusComponent),
                MockComponent(ButtonComponent),
                MockComponent(TextResultComponent),
                MockComponent(ComplaintsFormComponent),
                MockComponent(ComplaintsStudentViewComponent),
                MockPipe(HtmlForMarkdownPipe),
                MockPipe(ArtemisTranslatePipe),
                MockComponent(HeaderParticipationPageComponent),
                MockComponent(ResizeableContainerComponent),
                MockComponent(TeamParticipateInfoBoxComponent),
                MockComponent(TeamSubmissionSyncComponent),
                MockComponent(AdditionalFeedbackComponent),
                MockComponent(RatingComponent),
                MockDirective(NgModel),
                MockDirective(TranslateDirective),
            ],
            providers: [
                AlertService,
                { provide: ActivatedRoute, useValue: route },
                { provide: TextEditorService, useClass: MockTextEditorService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TextSubmissionService, useClass: MockTextSubmissionService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: CourseExerciseService, useClass: MockCourseExerciseService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TextEditorComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                textService = debugElement.injector.get(TextEditorService);
                textSubmissionService = TestBed.inject(TextSubmissionService);
                getTextForParticipationStub = jest.spyOn(textService, 'get');
                courseExerciseService = fixture.debugElement.injector.get(CourseExerciseService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should use inputValues if present instead of loading new details', fakeAsync(() => {
        comp.inputExercise = textExercise;
        comp.inputParticipation = participation;

        // @ts-ignore updateParticipation is private
        const updateParticipationSpy = jest.spyOn(comp, 'updateParticipation');
        // @ts-ignore setupComponentWithInputValuesSpy is private
        const setupComponentWithInputValuesSpy = jest.spyOn(comp, 'setupComponentWithInputValues');

        fixture.detectChanges();

        expect(getTextForParticipationStub).not.toHaveBeenCalled();
        expect(updateParticipationSpy).not.toHaveBeenCalled();
        expect(setupComponentWithInputValuesSpy).toHaveBeenCalled();
    }));

    it('should not allow to submit after the due date if there is no due date', fakeAsync(() => {
        const participationSubject = new BehaviorSubject<StudentParticipation>(participation);
        getTextForParticipationStub.mockReturnValue(participationSubject);
        comp.textExercise = textExercise;

        fixture.detectChanges();
        tick();

        expect(comp.isAllowedToSubmitAfterDueDate).toBeFalsy();
        expect(comp.isAlwaysActive).toBeTruthy();

        tick();
        fixture.destroy();
        flush();
    }));

    it('should not allow to submit after the due date if the initialization date is before the due date', fakeAsync(() => {
        participation.initializationDate = dayjs();
        textExercise.dueDate = dayjs().add(1, 'days');
        const participationSubject = new BehaviorSubject<StudentParticipation>(participation);
        getTextForParticipationStub.mockReturnValue(participationSubject);
        comp.textExercise = textExercise;

        fixture.detectChanges();
        tick();

        expect(comp.isAllowedToSubmitAfterDueDate).toBeFalsy();

        tick();
        fixture.destroy();
        flush();
    }));

    it('should allow to submit after the due date if the initialization date is after the due date', fakeAsync(() => {
        participation.initializationDate = dayjs().add(1, 'days');
        textExercise.dueDate = dayjs();
        const participationSubject = new BehaviorSubject<StudentParticipation>(participation);
        getTextForParticipationStub.mockReturnValue(participationSubject);
        comp.textExercise = textExercise;

        fixture.detectChanges();
        tick();

        expect(comp.isAllowedToSubmitAfterDueDate).toBeTruthy();

        tick();
        fixture.destroy();
        flush();
    }));

    it('should not be always active if there is a result and no due date', fakeAsync(() => {
        const participationSubject = new BehaviorSubject<StudentParticipation>(participation);
        getTextForParticipationStub.mockReturnValue(participationSubject);
        comp.result = result;
        comp.textExercise = textExercise;

        fixture.detectChanges();
        tick();

        expect(comp.isAlwaysActive).toBeFalsy();

        tick();
        fixture.destroy();
        flush();
    }));

    it('should be always active if there is no result and the initialization date is after the due date', fakeAsync(() => {
        const participationSubject = new BehaviorSubject<StudentParticipation>(participation);
        getTextForParticipationStub.mockReturnValue(participationSubject);
        comp.textExercise = textExercise;
        comp.textExercise.dueDate = dayjs();
        participation.initializationDate = dayjs().add(1, 'days');

        fixture.detectChanges();
        tick();

        expect(comp.isAlwaysActive).toBeTruthy();

        tick();
        fixture.destroy();
        flush();
    }));

    it('should get inactive as soon as the due date passes the current date', fakeAsync(() => {
        const participationSubject = new BehaviorSubject<StudentParticipation>(participation);
        getTextForParticipationStub.mockReturnValue(participationSubject);
        textExercise.dueDate = dayjs().add(1, 'days');
        participation.initializationDate = dayjs();

        fixture.detectChanges();
        tick();

        expect(comp.isActive).toBeTruthy();

        comp.textExercise.dueDate = dayjs().subtract(1, 'days');

        fixture.detectChanges();
        tick();

        expect(comp.isActive).toBeFalsy();

        tick();
        fixture.destroy();
        flush();
    }));

    it('should not submit while saving', () => {
        comp.isSaving = true;
        jest.spyOn(textSubmissionService, 'update');
        comp.submit();
        expect(textSubmissionService.update).not.toHaveBeenCalled();
    });

    it('should not submit without submission', () => {
        // @ts-ignore
        delete comp.submission;
        jest.spyOn(textSubmissionService, 'update');
        comp.submit();
        expect(textSubmissionService.update).not.toHaveBeenCalled();
    });

    it('should submit', () => {
        comp.participation = { id: 1 };
        comp.submission = { id: 1, participation: { id: 1 } as Participation } as TextSubmission;
        comp.textExercise = { id: 1 } as TextExercise;
        comp.answer = 'abc';
        jest.spyOn(textSubmissionService, 'update');
        comp.submit();
        expect(textSubmissionService.update).toHaveBeenCalledOnce();
        expect(comp.isSaving).toBeFalsy();
    });

    it('should alert successful on submit if not isAllowedToSubmitAfterDueDate', () => {
        const alertService = fixture.debugElement.injector.get(AlertService);
        const alertServiceSpy = jest.spyOn(alertService, 'success');
        comp.participation = { id: 1 };
        comp.submission = { id: 1, participation: { id: 1 } as Participation } as TextSubmission;
        comp.textExercise = { id: 1 } as TextExercise;
        comp.answer = 'abc';
        comp.isAllowedToSubmitAfterDueDate = false;
        jest.spyOn(textSubmissionService, 'update');
        comp.submit();
        expect(textSubmissionService.update).toHaveBeenCalledOnce();
        expect(alertServiceSpy).toHaveBeenCalledOnce();
    });

    it('should warn alert on submit if submitDueDateMissedAlert', () => {
        const alertService = fixture.debugElement.injector.get(AlertService);
        const alertServiceSpy = jest.spyOn(alertService, 'warning');
        comp.participation = { id: 1 };
        comp.submission = { id: 1, participation: { id: 1 } as Participation } as TextSubmission;
        comp.textExercise = { id: 1 } as TextExercise;
        comp.answer = 'abc';
        comp.isAllowedToSubmitAfterDueDate = true;
        jest.spyOn(textSubmissionService, 'update');
        comp.submit();
        expect(textSubmissionService.update).toHaveBeenCalledOnce();
        expect(alertServiceSpy).toHaveBeenCalledOnce();
    });

    it('should return submission for answer', () => {
        jest.spyOn(textService, 'predictLanguage');
        const submissionForAnswer = comp['submissionForAnswer']('abc');
        expect(submissionForAnswer.text).toBe('abc');
        expect(submissionForAnswer.language).toEqual(Language.ENGLISH);
    });

    it('should return unreferenced feedback', () => {
        comp.result = {
            id: 1,
            feedbacks: [
                {
                    id: 1,
                    reference: undefined,
                    type: FeedbackType.MANUAL_UNREFERENCED,
                } as Feedback,
            ],
        } as Result;
        const unreferencedFeedback = comp.unreferencedFeedback;
        expect(unreferencedFeedback?.length).toBe(1);
    });

    it('should receive submission from team', () => {
        comp.textExercise = {
            id: 1,
            studentParticipations: [] as StudentParticipation[],
        } as TextExercise;
        const submission = {
            id: 1,
            participation: {
                id: 1,
                exercise: { id: 1 } as Exercise,
                submissions: [] as Submission[],
            } as Participation,
            text: 'abc',
        } as TextSubmission;
        // @ts-ignore
        jest.spyOn(comp, 'updateParticipation');
        comp.onReceiveSubmissionFromTeam(submission);
        expect(comp['updateParticipation']).toHaveBeenCalledOnce();
        expect(comp.answer).toBe('abc');
    });

    it('should set latest submission if submissionId is undefined in updateParticipation', () => {
        const submissionList = [{ id: 1 }, { id: 2 }, { id: 3 }];

        const exGroup = {
            id: 1,
        };
        const textExercise = {
            type: ExerciseType.TEXT,
            dueDate: dayjs().add(5, 'minutes'),
            exerciseGroup: exGroup,
        } as TextExercise;
        comp.participation = {
            id: 2,
            submissions: submissionList,
            exercise: textExercise,
        } as StudentParticipation;
        comp['updateParticipation'](comp.participation, undefined);
        expect(comp.submission.id).toEqual(submissionList[submissionList.length - 1].id);
    });

    it('should set the correct submission if updateParticipation is called with submission id', () => {
        const submissionList = [{ id: 1 }, { id: 2 }, { id: 3 }];

        const exGroup = {
            id: 1,
        };
        const textExercise = {
            type: ExerciseType.TEXT,
            dueDate: dayjs().add(5, 'minutes'),
            exerciseGroup: exGroup,
        } as TextExercise;
        comp.participation = {
            id: 2,
            submissions: submissionList,
            exercise: textExercise,
        } as StudentParticipation;
        comp['updateParticipation'](comp.participation, 2);
        expect(comp.submission.id).toBe(2);
    });

    it('should sort results by completion date in ascending order', () => {
        const result1 = { completionDate: dayjs().subtract(2, 'days') } as Result;
        const result2 = { completionDate: dayjs().subtract(1, 'day') } as Result;
        const result3 = { completionDate: dayjs() } as Result;

        const results = [result3, result1, result2];
        results.sort((a, b) => comp['resultSortFunction'](a, b));

        expect(results).toEqual([result1, result2, result3]);
    });

    it('should sort submission by completion date in ascending order', () => {
        const submission1 = { completionDate: dayjs().subtract(2, 'days') } as Submission;
        const submission2 = { completionDate: dayjs().subtract(1, 'day') } as Submission;
        const submission3 = { completionDate: dayjs() } as Submission;

        const submissions = [submission3, submission1, submission2];
        submissions.sort((a, b) => comp['resultSortFunction'](a, b));

        expect(submissions).toEqual([submission1, submission2, submission3]);
    });

    it('assureConditionsSatisfied should alert and return false if the request is made after the due date', () => {
        const alertService = fixture.debugElement.injector.get(AlertService);
        const alertServiceSpy = jest.spyOn(alertService, 'warning');
        comp.textExercise = {
            type: ExerciseType.TEXT,
            dueDate: dayjs().subtract(5, 'minutes'),
        } as TextExercise;
        comp.participation = {
            id: 2,
            results: [
                {
                    assessmentType: AssessmentType.AUTOMATIC_ATHENA,
                    score: 100,
                },
            ],
        } as StudentParticipation;
        const result = comp.assureConditionsSatisfied();
        expect(alertServiceSpy).toHaveBeenCalledOnce();
        expect(alertServiceSpy).toHaveBeenCalledWith('artemisApp.exercise.feedbackRequestAfterDueDate');
        expect(result).toBeFalse();
    });

    it('assureConditionsSatisfied should alert and return false if there are pending changes', () => {
        const alertService = fixture.debugElement.injector.get(AlertService);
        const alertServiceSpy = jest.spyOn(alertService, 'warning');
        comp.textExercise = {
            type: ExerciseType.TEXT,
            dueDate: dayjs().add(5, 'minutes'),
        } as TextExercise;
        comp.participation = {
            id: 2,
            results: [
                {
                    assessmentType: AssessmentType.AUTOMATIC_ATHENA,
                    score: 100,
                },
            ],
        } as StudentParticipation;
        comp.submission = { id: 1, text: 'the same text' };
        comp.answer = 'not the same text';
        const result = comp.assureConditionsSatisfied();
        expect(alertServiceSpy).toHaveBeenCalledOnce();
        expect(alertServiceSpy).toHaveBeenCalledWith('artemisApp.exercise.feedbackRequestPendingChanges');
        expect(result).toBeFalse();
    });

    it('assureConditionsSatisfied should alert and return false if the latest submission already has athena result', () => {
        const alertService = fixture.debugElement.injector.get(AlertService);
        const alertServiceSpy = jest.spyOn(alertService, 'warning');
        const submission: Submission = { id: 42 };
        comp.textExercise = {
            type: ExerciseType.TEXT,
            dueDate: dayjs().add(5, 'minutes'),
        } as TextExercise;

        comp.hasAthenaResultForLatestSubmission = true;

        comp.participation = {
            id: 2,
            results: [
                {
                    assessmentType: AssessmentType.AUTOMATIC_ATHENA,
                    score: 100,
                    submission: submission,
                    successful: true,
                },
            ],
            submissions: [submission],
        } as StudentParticipation;
        comp.submission = { id: 1, text: 'the same text' };
        comp.answer = 'the same text';
        const result = comp.assureConditionsSatisfied();
        expect(alertServiceSpy).toHaveBeenCalledOnce();
        expect(alertServiceSpy).toHaveBeenCalledWith('artemisApp.exercise.submissionAlreadyHasAthenaResult');
        expect(result).toBeFalse();
    });

    it('assureConditionsSatisfied should alert and return false if the maximum number of successful Athena results is reached', () => {
        const alertService = fixture.debugElement.injector.get(AlertService);
        const alertServiceSpy = jest.spyOn(alertService, 'warning');
        const numResults = 20;
        const results: Array<{ assessmentType: AssessmentType; successful: boolean }> = [];

        for (let i = 0; i < numResults; i++) {
            results.push({ assessmentType: AssessmentType.AUTOMATIC_ATHENA, successful: true });
        }

        comp.textExercise = {
            type: ExerciseType.TEXT,
            dueDate: dayjs().add(5, 'minutes'),
        } as TextExercise;

        comp.hasAthenaResultForLatestSubmission = true;

        comp.participation = {
            id: 2,
            individualDueDate: undefined,
            results: [
                {
                    assessmentType: AssessmentType.AUTOMATIC_ATHENA,
                    score: 100,
                    successful: true,
                },
                ...results,
            ],
        } as StudentParticipation;
        comp.submission = { id: 1, text: 'the same text' };
        comp.answer = 'the same text';
        const result = comp.assureConditionsSatisfied();

        expect(alertServiceSpy).toHaveBeenCalledOnce();
        expect(result).toBeFalse();
    });

    it('assureConditionsSatisfied should return true if all conditions are satisfied', () => {
        comp.textExercise = {
            type: ExerciseType.TEXT,
            dueDate: dayjs().add(5, 'minutes'),
        } as TextExercise;
        comp.submission = { id: 1, text: 'the same text' };
        comp.answer = 'the same text';
        comp.participation = {
            id: 2,
            results: [
                {
                    assessmentType: AssessmentType.AUTOMATIC_ATHENA,
                    score: 100,
                },
            ],
        } as StudentParticipation;
        const result = comp.assureConditionsSatisfied();
        expect(result).toBeTrue();
    });

    it('should show request ai feedback if there is no due date', () => {
        comp.isReadOnlyWithShowResult = false;
        comp.isOwnerOfParticipation = true;
        comp.textExercise = textExercise;
        comp.textExercise.allowFeedbackRequests = true;
        comp.textExercise.dueDate = undefined;
        comp.submission = { id: 5, submitted: true };
        comp.hasAthenaResultForLatestSubmission = false;
        fixture.detectChanges();
        const resultHistoryElement: DebugElement = fixture.debugElement.query(By.css('#request-feedback'));
        expect(resultHistoryElement).toBeTruthy();
    });

    it('should not call courseExerciseService.requestFeedback if assureConditionsSatisfied returns false', () => {
        jest.spyOn(comp, 'assureConditionsSatisfied').mockReturnValue(false);
        const courseExerciseServiceSpy = jest.spyOn(courseExerciseService, 'requestFeedback');
        comp.requestFeedback();
        expect(comp.assureConditionsSatisfied).toHaveBeenCalled();
        expect(courseExerciseServiceSpy).not.toHaveBeenCalled();
    });

    it('should call courseExerciseService.requestFeedback if assureConditionsSatisfied returns true', () => {
        jest.spyOn(comp, 'assureConditionsSatisfied').mockReturnValue(true);
        const courseExerciseServiceSpy = jest.spyOn(courseExerciseService, 'requestFeedback');
        comp.textExercise = textExercise;
        comp.requestFeedback();
        expect(comp.assureConditionsSatisfied).toHaveBeenCalled();
        expect(courseExerciseServiceSpy).toHaveBeenCalled();
    });

    it('should not render the submit button when isReadOnlyWithShowResult is true', () => {
        comp.isReadOnlyWithShowResult = true;
        comp.textExercise = textExercise;
        fixture.detectChanges();

        const submitButton = fixture.debugElement.query(By.css('#submit'));
        expect(submitButton).toBeFalsy();
    });

    it('should render the submit button when isReadOnlyWithShowResult is false', () => {
        comp.isOwnerOfParticipation = true;
        comp.isReadOnlyWithShowResult = false;
        comp.isAlwaysActive = true;
        comp.textExercise = textExercise;
        comp.submission = { id: 5, submitted: true };

        fixture.detectChanges();

        const submitButton = fixture.debugElement.query(By.css('#submit'));
        expect(submitButton).toBeTruthy();
    });

    it('should destroy', () => {
        comp.submission = { text: 'abc' } as TextSubmission;
        comp.answer = 'def';
        comp.textExercise = { id: 1 } as TextExercise;
        jest.spyOn(textSubmissionService, 'update');
        comp.ngOnDestroy();
        expect(textSubmissionService.update).not.toHaveBeenCalled();
    });
});
