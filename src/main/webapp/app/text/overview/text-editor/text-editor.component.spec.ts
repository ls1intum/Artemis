/**
 * Test suite for TextEditorComponent.
 * Tests text submission, participation management, and editor functionality.
 */
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import dayjs from 'dayjs/esm';
import { ActivatedRoute, Params, RouterModule, convertToParamMap } from '@angular/router';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTextEditorService } from 'test/helpers/mocks/service/mock-text-editor.service';
import { TextEditorService } from 'app/text/overview/service/text-editor.service';
import { BehaviorSubject, Subject, of, throwError } from 'rxjs';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { TextResultComponent } from 'app/text/overview/text-result/text-result.component';
import { SubmissionResultStatusComponent } from 'app/course/overview/submission-result-status/submission-result-status.component';
import { TextEditorComponent } from 'app/text/overview/text-editor/text-editor.component';
import { textEditorRoute } from 'app/text/overview/text-editor.route';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { ButtonComponent } from 'app/shared-ui/components/buttons/button/button.component';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ComplaintsFormComponent } from 'app/assessment/overview/complaint-form/complaints-form.component';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { TextSubmissionService } from 'app/text/overview/service/text-submission.service';
import { MockTextSubmissionService } from 'test/helpers/mocks/service/mock-text-submission.service';
import { Language } from 'app/course/shared/entities/course.model';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { ResizeableContainerComponent } from 'app/shared-ui/resizeable-container/resizeable-container.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TeamParticipateInfoBoxComponent } from 'app/exercise/team/team-participate/team-participate-info-box.component';
import { TeamSubmissionSyncComponent } from 'app/exercise/team-submission-sync/team-submission-sync.component';
import { UnifiedFeedbackComponent } from 'app/shared/components/unified-feedback/unified-feedback.component';
import { RatingComponent } from 'app/exercise/rating/rating.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ComplaintsStudentViewComponent } from 'app/assessment/overview/complaints-for-students/complaints-student-view.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { By } from '@angular/platform-browser';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { Team } from 'app/exercise/shared/entities/team/team.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { RequestFeedbackButtonComponent } from 'app/course/overview/exercise-details/request-feedback-button/request-feedback-button.component';
import { FormsModule } from '@angular/forms';
import { Component, input } from '@angular/core';

// Mock components to avoid complex dependencies
@Component({ selector: 'jhi-request-feedback-button', template: '', standalone: true })
class MockRequestFeedbackButtonComponent {
    exercise = input<any>();
    pendingChanges = input<any>();
    hasAthenaResultForLatestSubmission = input<any>();
    isSubmitted = input<any>();
}

describe('TextEditorComponent', () => {
    let comp: TextEditorComponent;
    let fixture: ComponentFixture<TextEditorComponent>;
    let textService: TextEditorService;
    let textSubmissionService: TextSubmissionService;
    let getTextForParticipationStub: any;

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

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [
                RouterModule.forRoot([textEditorRoute[0]]),
                FaIconComponent,
                TextEditorComponent,
                MockComponent(SubmissionResultStatusComponent),
                MockComponent(ButtonComponent),
                MockComponent(TextResultComponent),
                MockComponent(ComplaintsFormComponent),
                MockComponent(ComplaintsStudentViewComponent),
                MockDirective(MarkdownDirective),
                MockPipe(ArtemisTranslatePipe),
                MockComponent(ResizeableContainerComponent),
                MockComponent(TeamParticipateInfoBoxComponent),
                MockComponent(TeamSubmissionSyncComponent),
                MockComponent(UnifiedFeedbackComponent),
                MockComponent(RatingComponent),
                MockDirective(TranslateDirective),
            ],
            providers: [
                AlertService,
                { provide: ActivatedRoute, useValue: route },
                { provide: TextEditorService, useClass: MockTextEditorService },
                LocalStorageService,
                SessionStorageService,
                { provide: TextSubmissionService, useClass: MockTextSubmissionService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .overrideComponent(TextEditorComponent, {
                remove: {
                    imports: [RequestFeedbackButtonComponent, TeamParticipateInfoBoxComponent, TeamSubmissionSyncComponent],
                },
                add: {
                    imports: [MockRequestFeedbackButtonComponent, FormsModule, MockComponent(TeamParticipateInfoBoxComponent), MockComponent(TeamSubmissionSyncComponent)],
                },
            })
            .compileComponents();
        fixture = TestBed.createComponent(TextEditorComponent);
        comp = fixture.componentInstance;
        textService = TestBed.inject(TextEditorService);
        textSubmissionService = TestBed.inject(TextSubmissionService);
        getTextForParticipationStub = vi.spyOn(textService, 'get');
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should use inputValues if present instead of loading new details', async () => {
        fixture.componentRef.setInput('inputExercise', textExercise);
        fixture.componentRef.setInput('inputParticipation', participation);
        fixture.componentRef.setInput('inputSubmission', { id: 1, text: 'test' });
        // @ts-ignore updateParticipation is private
        const updateParticipationSpy = vi.spyOn(comp, 'updateParticipation');
        // @ts-ignore setupComponentWithInputValuesSpy is private
        const setupComponentWithInputValuesSpy = vi.spyOn(comp, 'setupComponentWithInputValues');

        fixture.detectChanges();

        expect(getTextForParticipationStub).not.toHaveBeenCalled();
        expect(updateParticipationSpy).not.toHaveBeenCalled();
        expect(setupComponentWithInputValuesSpy).toHaveBeenCalled();
        expect(comp.answer()).toBeDefined();
    });

    it('should ignore participation changes that belong to a different participation', () => {
        // Regression test: subscribeForParticipationChanges() is backed by a single app-wide BehaviorSubject.
        // When several text editors are rendered together (e.g. multiple text exercises in the exam summary),
        // an emission for another participation must not overwrite this instance's state.
        fixture.componentRef.setInput('inputExercise', textExercise);
        fixture.componentRef.setInput('inputParticipation', participation);
        fixture.componentRef.setInput('inputSubmission', { id: 1, text: 'test' });
        fixture.detectChanges();

        // @ts-ignore updateParticipation is private
        const updateParticipationSpy = vi.spyOn(comp, 'updateParticipation').mockImplementation(() => {});
        const participationSubject = TestBed.inject(ParticipationWebsocketService).subscribeForParticipationChanges();

        const otherParticipation = new StudentParticipation();
        otherParticipation.id = 99;
        otherParticipation.exercise = { id: 2 } as TextExercise;
        otherParticipation.submissions = [new TextSubmission()];
        participationSubject.next(otherParticipation);

        expect(updateParticipationSpy).not.toHaveBeenCalled();

        // an emission for our own participation must still be applied
        participationSubject.next(participation);
        expect(updateParticipationSpy).toHaveBeenCalledExactlyOnceWith(participation, undefined, undefined);

        fixture.destroy();
    });

    it('should not be overwritten by a sibling text editor for a different participation (multi-instance)', () => {
        // The actual reported bug: several text editors render together in the exam summary and share the app-wide
        // participation-change subject. When a sibling editor initializes (addParticipation), its emission must not
        // overwrite this editor's exercise/submission.
        fixture.componentRef.setInput('inputExercise', textExercise);
        fixture.componentRef.setInput('inputParticipation', participation);
        fixture.componentRef.setInput('inputSubmission', { id: 1, text: 'A' });
        fixture.detectChanges();
        expect(comp.textExercise().id).toBe(1);
        expect(comp.submission().id).toBe(1);

        // A second editor for a DIFFERENT text exercise/participation initializes and pushes its participation.
        const fixture2 = TestBed.createComponent(TextEditorComponent);
        const otherExercise = { id: 2 } as TextExercise;
        const otherParticipation = new StudentParticipation();
        otherParticipation.id = 99;
        otherParticipation.exercise = otherExercise;
        otherParticipation.submissions = [{ id: 5, text: 'B' } as TextSubmission];
        fixture2.componentRef.setInput('inputExercise', otherExercise);
        fixture2.componentRef.setInput('inputParticipation', otherParticipation);
        fixture2.componentRef.setInput('inputSubmission', { id: 5, text: 'B' });
        fixture2.detectChanges();

        // This editor must still show its own exercise/submission, not the sibling's.
        expect(comp.textExercise().id).toBe(1);
        expect(comp.submission().id).toBe(1);

        fixture2.destroy();
        fixture.destroy();
    });

    it('should apply a participation-change emission with the same id but a different object reference', () => {
        // Real result updates arrive as a different object (the cached clone) with the same participation id. The guard
        // must compare by id, not by reference, so such own-participation updates are still applied.
        fixture.componentRef.setInput('inputExercise', textExercise);
        fixture.componentRef.setInput('inputParticipation', participation);
        fixture.componentRef.setInput('inputSubmission', { id: 1, text: 'test' });
        fixture.detectChanges();

        // @ts-ignore updateParticipation is private
        const updateParticipationSpy = vi.spyOn(comp, 'updateParticipation').mockImplementation(() => {});
        const participationSubject = TestBed.inject(ParticipationWebsocketService).subscribeForParticipationChanges();

        const sameIdDifferentObject = new StudentParticipation();
        sameIdDifferentObject.id = participation.id; // 42 - same participation, different object
        sameIdDifferentObject.exercise = textExercise;
        sameIdDifferentObject.submissions = [new TextSubmission()];
        participationSubject.next(sameIdDifferentObject);

        expect(updateParticipationSpy).toHaveBeenCalledExactlyOnceWith(sameIdDifferentObject, undefined, undefined);

        fixture.destroy();
    });

    it('should not allow to submit after the due date if there is no due date', async () => {
        const participationSubject = new BehaviorSubject<StudentParticipation>(participation);
        getTextForParticipationStub.mockReturnValue(participationSubject);
        comp.textExercise.set(textExercise);

        // @ts-ignore updateParticipation is private
        comp.updateParticipation(participation);

        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        expect(comp.isAllowedToSubmitAfterDueDate()).toBeFalsy();

        fixture.destroy();
    });

    it('should not allow to submit after the due date if the initialization date is before the due date', async () => {
        participation.initializationDate = dayjs();
        textExercise.dueDate = dayjs().add(1, 'days');
        const participationSubject = new BehaviorSubject<StudentParticipation>(participation);
        getTextForParticipationStub.mockReturnValue(participationSubject);
        comp.textExercise.set(textExercise);

        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        expect(comp.isAllowedToSubmitAfterDueDate()).toBeFalsy();

        fixture.destroy();
    });

    it('should allow to submit after the due date if the initialization date is after the due date', async () => {
        participation.initializationDate = dayjs().add(1, 'days');
        textExercise.dueDate = dayjs();
        const participationSubject = new BehaviorSubject<StudentParticipation>(participation);
        getTextForParticipationStub.mockReturnValue(participationSubject);
        comp.textExercise.set(textExercise);
        // @ts-ignore updateParticipation is private
        comp.updateParticipation(participation);

        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        expect(comp.isAllowedToSubmitAfterDueDate()).toBeTruthy();

        fixture.destroy();
    });

    it('should get inactive as soon as the due date passes the current date', async () => {
        const participationSubject = new BehaviorSubject<StudentParticipation>(participation);
        getTextForParticipationStub.mockReturnValue(participationSubject);
        textExercise.dueDate = dayjs().add(1, 'days');
        participation.initializationDate = dayjs();
        // @ts-ignore updateParticipation is private
        comp.updateParticipation(participation);

        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        expect(comp.isActive).toBeTruthy();

        comp.textExercise().dueDate = dayjs().subtract(1, 'days');

        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        expect(comp.isActive).toBeFalsy();

        fixture.destroy();
    });

    it('should not submit while saving', () => {
        comp.isSaving.set(true);
        vi.spyOn(textSubmissionService, 'update');
        comp.submitExercise();
        expect(textSubmissionService.update).not.toHaveBeenCalled();
    });

    it('should not submit without submission', () => {
        comp.submission.set(undefined!);
        vi.spyOn(textSubmissionService, 'update');
        comp.submitExercise();
        expect(textSubmissionService.update).not.toHaveBeenCalled();
    });

    it('should submit', () => {
        comp.participation.set({ id: 1 });
        comp.submission.set({ id: 1, participation: { id: 1 } as Participation } as TextSubmission);
        comp.textExercise.set({ id: 1 } as TextExercise);
        comp.answer.set('abc');
        vi.spyOn(textSubmissionService, 'update');
        comp.submitExercise();
        expect(textSubmissionService.update).toHaveBeenCalledOnce();
        expect(comp.isSaving()).toBeFalsy();
    });

    it('should alert successful on submit if not isAllowedToSubmitAfterDueDate', () => {
        const alertService = TestBed.inject(AlertService);
        const alertServiceSpy = vi.spyOn(alertService, 'success');
        comp.participation.set({ id: 1 });
        comp.submission.set({ id: 1, participation: { id: 1 } as Participation } as TextSubmission);
        comp.textExercise.set({ id: 1 } as TextExercise);
        comp.answer.set('abc');
        comp.isAllowedToSubmitAfterDueDate.set(false);
        vi.spyOn(textSubmissionService, 'update');
        comp.submitExercise();
        expect(textSubmissionService.update).toHaveBeenCalledOnce();
        expect(alertServiceSpy).toHaveBeenCalledOnce();
    });

    it('should warn alert on submit if submitDueDateMissedAlert', () => {
        const alertService = TestBed.inject(AlertService);
        const alertServiceSpy = vi.spyOn(alertService, 'warning');
        comp.participation.set({ id: 1 });
        comp.submission.set({ id: 1, participation: { id: 1 } as Participation } as TextSubmission);
        comp.textExercise.set({ id: 1 } as TextExercise);
        comp.answer.set('abc');
        comp.isAllowedToSubmitAfterDueDate.set(true);
        vi.spyOn(textSubmissionService, 'update');
        comp.submitExercise();
        expect(textSubmissionService.update).toHaveBeenCalledOnce();
        expect(alertServiceSpy).toHaveBeenCalledOnce();
    });

    it('should return submission for answer', () => {
        vi.spyOn(textService, 'predictLanguage');
        const submissionForAnswer = comp['submissionForAnswer']('abc');
        expect(submissionForAnswer.text).toBe('abc');
        expect(submissionForAnswer.language).toEqual(Language.ENGLISH);
    });

    it('should return unreferenced feedback', () => {
        comp.result.set({
            id: 1,
            feedbacks: [
                {
                    id: 1,
                    reference: undefined,
                    type: FeedbackType.MANUAL_UNREFERENCED,
                } as Feedback,
            ],
        } as Result);
        const unreferencedFeedback = comp.unreferencedFeedback;
        expect(unreferencedFeedback?.length).toBe(1);
    });

    it('should receive submission from team', () => {
        comp.participation.set({ id: 1, team: { id: 1 } } as StudentParticipation);
        comp.textExercise.set({
            id: 1,
            studentParticipations: [] as StudentParticipation[],
        } as TextExercise);
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
        vi.spyOn(comp, 'updateParticipation');
        comp.onReceiveSubmissionFromTeam(submission);
        expect(comp['updateParticipation']).toHaveBeenCalledOnce();
        expect(comp.answer()).toBe('abc');
    });

    it('should receive empty submission from team', () => {
        comp.participation.set({ id: 1, team: { id: 1 } } as StudentParticipation);
        comp.textExercise.set({
            id: 1,
            studentParticipations: [] as StudentParticipation[],
        } as TextExercise);
        const submission = {
            id: 1,
            participation: {
                id: 1,
                exercise: { id: 1 } as Exercise,
                submissions: [] as Submission[],
            } as Participation,
        } as TextSubmission;
        // @ts-ignore
        vi.spyOn(comp, 'updateParticipation');
        comp.onReceiveSubmissionFromTeam(submission);
        expect(comp['updateParticipation']).toHaveBeenCalledOnce();
        expect(comp.answer()).toBe('');
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
        comp.participation.set({
            id: 2,
            submissions: submissionList,
            exercise: textExercise,
        } as StudentParticipation);
        comp['updateParticipation'](comp.participation(), undefined);
        expect(comp.submission().id).toEqual(submissionList[submissionList.length - 1].id);
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
        comp.participation.set({
            id: 2,
            submissions: submissionList,
            exercise: textExercise,
        } as StudentParticipation);
        comp['updateParticipation'](comp.participation(), 2);
        expect(comp.submission().id).toBe(2);
    });

    it('should set the correct result when updateParticipation is called with resultId', () => {
        const manualResult = { id: 99, assessmentType: AssessmentType.MANUAL, score: 85, completionDate: dayjs() } as Result;
        const athenaResult = { id: 100, assessmentType: AssessmentType.AUTOMATIC_ATHENA, score: 75, completionDate: dayjs() } as Result;
        const submissionList = [{ id: 2, results: [manualResult, athenaResult], latestResult: athenaResult }];

        const textExercise = {
            type: ExerciseType.TEXT,
            dueDate: dayjs().add(5, 'minutes'),
            course: { id: 1 },
            assessmentDueDate: dayjs().add(6, 'minutes'),
        } as TextExercise;
        comp.participation.set({
            id: 2,
            submissions: submissionList,
            exercise: textExercise,
        } as StudentParticipation);

        // Call with submissionId and resultId to select specific manual result
        comp['updateParticipation'](comp.participation(), 2, 99);

        expect(comp.submission().id).toBe(2);
        expect(comp.result()?.id).toBe(99);
        expect(comp.result()?.assessmentType).toBe(AssessmentType.MANUAL);
        expect(comp.result()?.score).toBe(85);
    });

    it('should set the latest submission if updateParticipation is called with submission id that does not exist', () => {
        const submissionList = [{ id: 1 }, { id: 3 }, { id: 4, results: [{ id: 1, assessmentType: AssessmentType.MANUAL }] }];

        const exGroup = {
            id: 1,
        };
        const textExercise = {
            type: ExerciseType.TEXT,
            dueDate: dayjs().add(5, 'minutes'),
            exerciseGroup: exGroup,
            course: { id: 1 },
            assessmentDueDate: dayjs().add(6, 'minutes'),
        } as TextExercise;
        comp.participation.set({
            id: 2,
            submissions: submissionList,
            exercise: textExercise,
            results: [{ id: 1 }, { id: 2 }],
        } as StudentParticipation);
        comp['updateParticipation'](comp.participation(), 2);
        expect(comp.submission().id).toBe(4);
    });

    it('should hide the textarea when there is an Athena result and isExamSummary is true', () => {
        comp.textExercise.set(textExercise);
        comp.result.set({ id: 1, assessmentType: AssessmentType.AUTOMATIC_ATHENA } as Result);
        fixture.componentRef.setInput('isExamSummary', true);
        fixture.changeDetectorRef.detectChanges();

        const textarea = fixture.debugElement.query(By.css('#text-editor'));
        expect(textarea).toBeFalsy();
    });

    it('should render the textarea when there is an Athena result and isExamSummary is false', () => {
        comp.textExercise.set(textExercise);
        comp.result.set({ id: 1, assessmentType: AssessmentType.AUTOMATIC_ATHENA } as Result);
        fixture.componentRef.setInput('isExamSummary', false);
        fixture.changeDetectorRef.detectChanges();

        const textarea = fixture.debugElement.query(By.css('#text-editor'));
        expect(textarea).toBeTruthy();
    });

    describe('team collaboration', () => {
        const teamExercise = { id: 1, teamMode: true, dueDate: dayjs().add(1, 'day') } as TextExercise;

        const setUp = (testRun: boolean) => {
            const teamParticipation = { id: 42, testRun, exercise: teamExercise, submissions: [new TextSubmission()] } as StudentParticipation;
            comp.textExercise.set(teamExercise);
            comp.participation.set(teamParticipation);
        };

        it('should collaborate with the team in the graded participation of a team exercise', () => {
            setUp(false);

            expect(comp.teamCollaborationEnabled()).toBe(true);
        });

        // positive control of the practice test below: the same exercise, state and rendering show both team components for the graded participation
        it('should show the team info box and synchronize with the team for the graded participation of a team exercise', () => {
            setUp(false);
            fixture.changeDetectorRef.detectChanges();

            expect(fixture.debugElement.query(By.css('jhi-team-participate-info-box'))).toBeTruthy();
            expect(fixture.debugElement.query(By.css('jhi-team-submission-sync'))).toBeTruthy();
            expect(fixture.debugElement.query(By.css('#text-editor'))).toBeTruthy();
        });

        it('should not show the team components in an individual exercise', () => {
            comp.textExercise.set({ id: 1, teamMode: false, dueDate: dayjs().add(1, 'day') } as TextExercise);
            comp.participation.set({ id: 42, testRun: false, submissions: [new TextSubmission()] } as StudentParticipation);
            fixture.changeDetectorRef.detectChanges();

            expect(fixture.debugElement.query(By.css('jhi-team-participate-info-box'))).toBeFalsy();
            expect(fixture.debugElement.query(By.css('jhi-team-submission-sync'))).toBeFalsy();
            expect(fixture.debugElement.query(By.css('#text-editor'))).toBeTruthy();
        });

        it('should neither show the team info box nor synchronize with the team for the individual practice participation of a team exercise', () => {
            setUp(true);
            fixture.changeDetectorRef.detectChanges();

            expect(comp.teamCollaborationEnabled()).toBe(false);
            expect(fixture.debugElement.query(By.css('jhi-team-participate-info-box'))).toBeFalsy();
            expect(fixture.debugElement.query(By.css('jhi-team-submission-sync'))).toBeFalsy();
            // the editor itself is rendered, the practice participation is edited alone
            expect(fixture.debugElement.query(By.css('#text-editor'))).toBeTruthy();
        });

        it('should not collaborate with a team in an individual exercise', () => {
            comp.textExercise.set({ id: 1, teamMode: false } as TextExercise);
            comp.participation.set({ id: 42, testRun: false } as StudentParticipation);

            expect(comp.teamCollaborationEnabled()).toBe(false);
        });
    });

    describe('card layout', () => {
        const getLeftBody = (): HTMLElement => fixture.nativeElement.querySelector('[left-body]');

        const renderAssessedSubmission = () => {
            comp.textExercise.set(textExercise);
            comp.result.set({ id: 1, feedbacks: [{ id: 1, type: FeedbackType.MANUAL_UNREFERENCED, detailText: 'feedback' } as Feedback] } as Result);
            comp.isOwnerOfParticipation.set(true);
            fixture.changeDetectorRef.detectChanges();
        };

        it('should keep the left padding of the left body on the participation page', () => {
            comp.textExercise.set(textExercise);
            fixture.componentRef.setInput('isExamSummary', false);
            fixture.changeDetectorRef.detectChanges();

            expect(getLeftBody().classList).toContain('ps-2');
        });

        it('should drop the left padding of the left body in the exam summary so the body shares the card inset', () => {
            comp.textExercise.set(textExercise);
            fixture.componentRef.setInput('isExamSummary', true);
            fixture.changeDetectorRef.detectChanges();

            expect(getLeftBody().classList).not.toContain('ps-2');
            expect(getLeftBody().classList).toContain('pb-2');
        });

        it('should render the rating inside its own column so it lines up with the feedback above, without an alert box', () => {
            renderAssessedSubmission();

            const rating: HTMLElement = fixture.nativeElement.querySelector('jhi-rating');
            expect(rating).toBeTruthy();
            // The rating is a quiet reaction to the feedback, so the page no longer wraps it in a coloured alert.
            expect(rating.classList).not.toContain('alert');
            expect(rating.classList).not.toContain('alert-info');
            expect(rating.classList).not.toContain('col-xl-8');
            const column = rating.parentElement!;
            expect(column.classList).toContain('col-xl-8');
            expect(column.classList).toContain('col-lg-10');
            expect(column.classList).not.toContain('alert');
            expect(column.parentElement!.classList).toContain('row');
        });

        it('should render the assessed submission label with the section heading style', () => {
            renderAssessedSubmission();

            const label: HTMLElement = fixture.nativeElement.querySelector('.col-xl-8 > b');
            expect(label).toBeTruthy();
            expect(label.classList).toContain('text-base');
            expect(label.classList).toContain('font-semibold!');
        });
    });

    it('should destroy', () => {
        comp.submission.set({ text: 'abc' } as TextSubmission);
        comp.answer.set('def');
        comp.textExercise.set({ id: 1 } as TextExercise);
        vi.spyOn(textSubmissionService, 'update');
        comp.ngOnDestroy();
        expect(textSubmissionService.update).not.toHaveBeenCalled();
    });

    it('should destroy and call submission service when submission id', () => {
        comp.submission.set({ id: 1, text: 'abc' } as TextSubmission);
        comp.answer.set('def');
        comp.textExercise.set({ id: 1 } as TextExercise);
        vi.spyOn(textSubmissionService, 'update');
        comp.ngOnDestroy();
        expect(textSubmissionService.update).toHaveBeenCalled();
    });

    it('isAutomaticResult reflects automatic Athena result', () => {
        comp.result.set({ assessmentType: AssessmentType.AUTOMATIC_ATHENA } as Result);
        expect(comp.isAutomaticResult).toBe(true);
        comp.result.set({ assessmentType: AssessmentType.MANUAL } as Result);
        expect(comp.isAutomaticResult).toBe(false);
    });

    it('canDeactivate true when no submission or unchanged; false when changed', () => {
        // no submission
        comp.submission.set(undefined!);
        expect(comp.canDeactivate()).toBe(true);
        // unchanged
        comp.submission.set({ text: 'same' } as TextSubmission);
        comp.answer.set('same');
        expect(comp.canDeactivate()).toBe(true);
        // changed
        comp.answer.set('different');
        expect(comp.canDeactivate()).toBe(false);
        // cleanup to avoid ngOnDestroy side-effects
        comp.submission.set(undefined as any);
    });

    it('unloadNotification returns translation key when there are unsaved changes', () => {
        const translate = TestBed.inject(TranslateService) as any;
        vi.spyOn(translate, 'instant');
        comp.submission.set({ text: 'before' } as TextSubmission);
        comp.answer.set('after');
        const event = new Event('beforeunload') as unknown as BeforeUnloadEvent;
        const res = comp.unloadNotification(event);
        expect(translate.instant).toHaveBeenCalledWith('pendingChanges');
        expect(res).toBe('pendingChanges');
        // cleanup to avoid ngOnDestroy side-effects
        comp.submission.set(undefined as any);
    });

    it('unloadNotification does not block leaving the page without unsaved changes', () => {
        comp.submission.set({ text: 'same' } as TextSubmission);
        comp.answer.set('same');
        const event = new Event('beforeunload') as unknown as BeforeUnloadEvent;
        const preventDefaultSpy = vi.spyOn(event, 'preventDefault');

        expect(comp.unloadNotification(event)).toBe(true);
        expect(preventDefaultSpy).not.toHaveBeenCalled();
        // cleanup to avoid ngOnDestroy side-effects
        comp.submission.set(undefined as any);
    });

    describe('loading the participation', () => {
        const originalSnapshot = route.snapshot;
        let routeParams: Subject<Params>;

        /** A fresh participation per test, because the component writes into the exercise and the participation it receives. */
        const loadedParticipation = (id = 42, submissions: TextSubmission[] = [{ id: 7, text: 'saved text' } as TextSubmission]) =>
            ({ id, testRun: true, exercise: { id: 1, type: ExerciseType.TEXT, course: { id: 3 } } as TextExercise, submissions }) as StudentParticipation;

        const setRouteParams = (paramMap: Record<string, string>) => {
            (route as { snapshot: unknown }).snapshot = { paramMap: convertToParamMap(paramMap) };
        };

        beforeEach(() => {
            routeParams = new Subject<Params>();
            (route as { params?: unknown }).params = routeParams;
        });

        afterEach(() => {
            (route as { snapshot: unknown }).snapshot = originalSnapshot;
            delete (route as { params?: unknown }).params;
        });

        it('should alert an error and load nothing when the participation id of the route is not a number', () => {
            setRouteParams({ participationId: 'abc' });
            const errorSpy = vi.spyOn(TestBed.inject(AlertService), 'error');

            fixture.detectChanges();

            expect(errorSpy).toHaveBeenCalledExactlyOnceWith('artemisApp.textExercise.error');
            expect(getTextForParticipationStub).not.toHaveBeenCalled();
        });

        it('should load the participation of the input directly and show a selected submission read-only', () => {
            setRouteParams({ participationId: '42', submissionId: '7' });
            const loaded = loadedParticipation();
            getTextForParticipationStub.mockReturnValue(of(loaded));
            const addParticipationSpy = vi.spyOn(TestBed.inject(ParticipationWebsocketService), 'addParticipation');
            fixture.componentRef.setInput('participationId', 42);

            fixture.detectChanges();

            expect(getTextForParticipationStub).toHaveBeenCalledExactlyOnceWith(42, undefined);
            expect(comp.participation()).toBe(loaded);
            expect(comp.submission().id).toBe(7);
            expect(comp.answer()).toBe('saved text');
            expect(comp.isReadOnlyWithShowResult()).toBe(true);
            expect(addParticipationSpy).toHaveBeenCalledOnce();
        });

        it('should not show the input participation read-only without a selected submission', () => {
            getTextForParticipationStub.mockReturnValue(of(loadedParticipation()));
            fixture.componentRef.setInput('participationId', 42);

            fixture.detectChanges();

            expect(comp.isReadOnlyWithShowResult()).toBe(false);
        });

        it('should alert when the participation of the input cannot be loaded', () => {
            getTextForParticipationStub.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
            const errorSpy = vi.spyOn(TestBed.inject(AlertService), 'error');
            fixture.componentRef.setInput('participationId', 42);

            fixture.detectChanges();

            expect(errorSpy).toHaveBeenCalledExactlyOnceWith('error.http.404');
        });

        it('should load the participation of the route and reload it only when the participation, submission or result changes', () => {
            setRouteParams({ participationId: '42' });
            getTextForParticipationStub.mockImplementation((id: number) => of(loadedParticipation(id)));
            fixture.detectChanges();

            routeParams.next({ participationId: '42' });
            expect(getTextForParticipationStub).toHaveBeenCalledExactlyOnceWith(42, undefined);
            expect(comp.participation().id).toBe(42);
            expect(comp.isReadOnlyWithShowResult()).toBe(false);

            // the same route again is applied to the participation that is already loaded
            routeParams.next({ participationId: '42' });
            expect(getTextForParticipationStub).toHaveBeenCalledOnce();

            // another participation
            routeParams.next({ participationId: '43' });
            expect(getTextForParticipationStub).toHaveBeenLastCalledWith(43, undefined);
            expect(comp.participation().id).toBe(43);

            // a selected submission
            setRouteParams({ participationId: '43', submissionId: '7' });
            routeParams.next({ participationId: '43' });
            expect(getTextForParticipationStub).toHaveBeenCalledTimes(3);
            expect(comp.isReadOnlyWithShowResult()).toBe(true);

            // a selected result
            setRouteParams({ participationId: '43', submissionId: '7', resultId: '5' });
            routeParams.next({ participationId: '43' });
            expect(getTextForParticipationStub).toHaveBeenLastCalledWith(43, 5);
        });

        it('should reload the loaded participation when the route changes the submission without a participation id', () => {
            setRouteParams({ participationId: '42' });
            getTextForParticipationStub.mockImplementation((id: number) => of(loadedParticipation(id)));
            fixture.detectChanges();
            routeParams.next({ participationId: '42' });
            getTextForParticipationStub.mockClear();

            setRouteParams({ submissionId: '7' });
            routeParams.next({});

            expect(getTextForParticipationStub).toHaveBeenCalledExactlyOnceWith(42, undefined);
        });

        it('should load nothing when the route changes the submission but no participation is known', () => {
            setRouteParams({ participationId: '42' });
            fixture.detectChanges();

            setRouteParams({ submissionId: '7' });
            routeParams.next({});

            expect(getTextForParticipationStub).not.toHaveBeenCalled();
            expect(comp.participation()).toBeUndefined();
        });

        it('should alert when the participation of the route cannot be loaded', () => {
            setRouteParams({ participationId: '42' });
            getTextForParticipationStub.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
            const errorSpy = vi.spyOn(TestBed.inject(AlertService), 'error');
            fixture.detectChanges();

            routeParams.next({ participationId: '42' });

            expect(errorSpy).toHaveBeenCalledExactlyOnceWith('error.http.403');
        });

        it('should keep the state when an update without a participation arrives', () => {
            setRouteParams({ participationId: '42' });
            getTextForParticipationStub.mockReturnValue(of(loadedParticipation()));
            fixture.detectChanges();
            routeParams.next({ participationId: '42' });
            const loaded = comp.participation();

            comp['updateParticipation'](undefined as unknown as StudentParticipation);

            expect(comp.participation()).toBe(loaded);
        });
    });

    describe('participation changes of the websocket', () => {
        const athenaResult = (successful: boolean | undefined) =>
            ({ id: 5, assessmentType: AssessmentType.AUTOMATIC_ATHENA, successful, completionDate: dayjs().subtract(1, 'minute') }) as Result;

        const withResults = (results: Result[]) =>
            ({
                id: 42,
                testRun: false,
                exercise: { id: 1, title: 'Essay', type: ExerciseType.TEXT, course: { id: 3 } } as TextExercise,
                submissions: [{ id: 7, text: 'answer', results }],
            }) as unknown as StudentParticipation;

        const openWithParticipation = (initial: StudentParticipation, isExamSummary = false) => {
            fixture.componentRef.setInput('inputExercise', initial.exercise);
            fixture.componentRef.setInput('inputParticipation', initial);
            fixture.componentRef.setInput('isExamSummary', isExamSummary);
            fixture.detectChanges();
            return TestBed.inject(ParticipationWebsocketService).subscribeForParticipationChanges();
        };

        it('should ignore an emission without a participation', () => {
            const participationSubject = openWithParticipation(withResults([]));
            const updateParticipationSpy = vi.spyOn(comp, 'updateParticipation' as never);

            participationSubject.next(undefined);

            expect(updateParticipationSpy).not.toHaveBeenCalled();
        });

        it('should alert a failed Athena feedback request and still apply the participation', () => {
            const participationSubject = openWithParticipation(withResults([]));
            const errorSpy = vi.spyOn(TestBed.inject(AlertService), 'error');

            participationSubject.next(withResults([athenaResult(false)]));

            expect(errorSpy).toHaveBeenCalledExactlyOnceWith('artemisApp.exercise.athenaFeedbackFailed');
            expect(comp.result().id).toBe(5);
            expect(comp.hasAthenaResultForLatestSubmission).toBe(true);
        });

        it('should alert a successful Athena feedback request and remember that the latest submission has feedback', () => {
            const participationSubject = openWithParticipation(withResults([]));
            const successSpy = vi.spyOn(TestBed.inject(AlertService), 'success');
            comp.hasAthenaResultForLatestSubmission = false;

            participationSubject.next(withResults([athenaResult(true)]));

            expect(successSpy).toHaveBeenCalledExactlyOnceWith('artemisApp.exercise.athenaFeedbackSuccessful', { title: 'Essay' });
            expect(comp.hasAthenaResultForLatestSubmission).toBe(true);
            expect(comp.result().id).toBe(5);
        });

        it('should reload the participation with the Athena result in the exam summary', () => {
            const participationSubject = openWithParticipation(withResults([]), true);
            const reloaded = withResults([athenaResult(true)]);
            getTextForParticipationStub.mockReturnValue(of(reloaded));

            participationSubject.next(withResults([athenaResult(true)]));

            expect(getTextForParticipationStub).toHaveBeenCalledExactlyOnceWith(42, 5);
            expect(comp.participation()).toBe(reloaded);
        });

        it('should alert when the participation with the Athena result cannot be reloaded in the exam summary', () => {
            const participationSubject = openWithParticipation(withResults([]), true);
            getTextForParticipationStub.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
            const errorSpy = vi.spyOn(TestBed.inject(AlertService), 'error');

            participationSubject.next(withResults([athenaResult(true)]));

            expect(errorSpy).toHaveBeenCalledWith('error.http.403');
        });

        it('should not treat an unfinished Athena request as new feedback', () => {
            const participationSubject = openWithParticipation(withResults([]));
            const alertService = TestBed.inject(AlertService);
            const successSpy = vi.spyOn(alertService, 'success');
            const errorSpy = vi.spyOn(alertService, 'error');

            participationSubject.next(withResults([athenaResult(undefined)]));

            expect(successSpy).not.toHaveBeenCalled();
            expect(errorSpy).not.toHaveBeenCalled();
        });
    });

    describe('submitting', () => {
        const prepareSubmission = (submission: TextSubmission, participation: StudentParticipation = { id: 1 } as StudentParticipation) => {
            comp.participation.set(participation);
            comp.submission.set(submission);
            comp.textExercise.set({ id: 1 } as TextExercise);
            comp.answer.set('abc');
        };

        it('should submit as a new submission without results when the latest submission already has Athena feedback', () => {
            prepareSubmission({
                id: 3,
                results: [{ id: 5, assessmentType: AssessmentType.AUTOMATIC_ATHENA } as Result],
                participation: { id: 1 } as Participation,
            } as TextSubmission);
            comp.hasAthenaResultForLatestSubmission = true;
            const updateSpy = vi.spyOn(textSubmissionService, 'update');

            comp.submitExercise();

            const submitted = updateSpy.mock.calls[0][0];
            expect(submitted.id).toBeUndefined();
            expect(submitted.results).toBeUndefined();
            expect(submitted.text).toBe('abc');
            expect(comp.hasAthenaResultForLatestSubmission).toBe(false);
        });

        it('should keep the id and the results of the submission without Athena feedback', () => {
            prepareSubmission({ id: 3, results: [{ id: 5, assessmentType: AssessmentType.MANUAL } as Result], participation: { id: 1 } as Participation } as TextSubmission);
            const updateSpy = vi.spyOn(textSubmissionService, 'update');

            comp.submitExercise();

            expect(updateSpy.mock.calls[0][0].id).toBe(3);
            expect(updateSpy.mock.calls[0][0].results).toHaveLength(1);
        });

        it('should not lose the team of the participation when the server answers without it', () => {
            const team = { id: 9 } as Team;
            prepareSubmission({ id: 3, participation: { id: 1 } as Participation } as TextSubmission, { id: 1, team } as StudentParticipation);

            comp.submitExercise();

            expect(comp.participation().team).toBe(team);
        });

        it('should alert the message of the server and allow submitting again when the submission fails', () => {
            prepareSubmission({ id: 3, participation: { id: 1 } as Participation } as TextSubmission);
            vi.spyOn(textSubmissionService, 'update').mockReturnValue(throwError(() => ({ error: { message: 'submission failed' } })));
            const errorSpy = vi.spyOn(TestBed.inject(AlertService), 'error');

            comp.submitExercise();

            expect(errorSpy).toHaveBeenCalledExactlyOnceWith('submission failed');
            expect(comp.isSaving()).toBe(false);
        });
    });

    describe('typing', () => {
        afterEach(() => {
            vi.useRealTimers();
        });

        it('should emit the submission with the typed text after the debounce time', () => {
            vi.useFakeTimers();
            comp.textExercise.set({ id: 1 } as TextExercise);
            comp.submission.set(new TextSubmission());
            const emitted: TextSubmission[] = [];
            const subscription = comp.submissionObservable.subscribe((submission) => emitted.push(submission));

            comp.onTextEditorInput({ target: { value: 'first' } } as unknown as Event);
            comp.onTextEditorInput({ target: { value: 'second' } } as unknown as Event);
            vi.advanceTimersByTime(1999);
            expect(emitted).toHaveLength(0);
            vi.advanceTimersByTime(1);

            expect(emitted).toHaveLength(1);
            expect(emitted[0].text).toBe('second');
            expect(emitted[0].language).toBe(Language.ENGLISH);
            subscription.unsubscribe();
        });
    });
});
