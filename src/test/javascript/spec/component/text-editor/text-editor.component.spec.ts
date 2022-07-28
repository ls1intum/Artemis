import * as ace from 'brace';
import { DebugElement } from '@angular/core';
import dayjs from 'dayjs/esm';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { AlertService } from 'app/core/util/alert.service';
import { ArtemisTestModule } from '../../test.module';
import { TranslateService } from '@ngx-translate/core';
import { MockTextEditorService } from '../../helpers/mocks/service/mock-text-editor.service';
import { TextEditorService } from 'app/exercises/text/participate/text-editor.service';
import { BehaviorSubject } from 'rxjs';
import { RouterTestingModule } from '@angular/router/testing';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { TextResultComponent } from 'app/exercises/text/participate/text-result/text-result.component';
import { SubmissionResultStatusComponent } from 'app/overview/submission-result-status.component';
import { TextEditorComponent } from 'app/exercises/text/participate/text-editor.component';
import { textEditorRoute } from 'app/exercises/text/participate/text-editor.route';
import { TextExercise } from 'app/entities/text-exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ButtonComponent } from 'app/shared/components/button.component';
import { Result } from 'app/entities/result.model';
import { ComplaintsFormComponent } from 'app/complaints/form/complaints-form.component';
import { TextSubmission } from 'app/entities/text-submission.model';
import { TextSubmissionService } from 'app/exercises/text/participate/text-submission.service';
import { MockTextSubmissionService } from '../../helpers/mocks/service/mock-text-submission.service';
import { Language } from 'app/entities/tutor-group.model';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { Participation } from 'app/entities/participation/participation.model';
import { Exercise } from 'app/entities/exercise.model';
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

describe('TextEditorComponent', () => {
    // needed to make sure ace is defined
    ace.acequire('ace/ext/modelist.js');

    let comp: TextEditorComponent;
    let fixture: ComponentFixture<TextEditorComponent>;
    let debugElement: DebugElement;
    let textService: TextEditorService;
    let textSubmissionService: TextSubmissionService;

    let getTextForParticipationStub: jest.SpyInstance;

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
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([textEditorRoute[0]])],
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
            ],
            providers: [
                AlertService,
                { provide: ActivatedRoute, useValue: route },
                { provide: TextEditorService, useClass: MockTextEditorService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TextSubmissionService, useClass: MockTextSubmissionService },
                { provide: TranslateService, useClass: MockTranslateService },
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
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should not allow to submit after the deadline if there is no due date', fakeAsync(() => {
        const participationSubject = new BehaviorSubject<StudentParticipation>(participation);
        getTextForParticipationStub.mockReturnValue(participationSubject);
        comp.textExercise = textExercise;

        fixture.detectChanges();
        tick();

        expect(comp.isAllowedToSubmitAfterDeadline).toBeFalsy();
        expect(comp.isAlwaysActive).toBeTruthy();

        tick();
        fixture.destroy();
        flush();
    }));

    it('should not allow to submit after the deadline if the initialization date is before the due date', fakeAsync(() => {
        participation.initializationDate = dayjs();
        textExercise.dueDate = dayjs().add(1, 'days');
        const participationSubject = new BehaviorSubject<StudentParticipation>(participation);
        getTextForParticipationStub.mockReturnValue(participationSubject);
        comp.textExercise = textExercise;

        fixture.detectChanges();
        tick();

        expect(comp.isAllowedToSubmitAfterDeadline).toBeFalsy();

        tick();
        fixture.destroy();
        flush();
    }));

    it('should allow to submit after the deadline if the initialization date is after the due date', fakeAsync(() => {
        participation.initializationDate = dayjs().add(1, 'days');
        textExercise.dueDate = dayjs();
        const participationSubject = new BehaviorSubject<StudentParticipation>(participation);
        getTextForParticipationStub.mockReturnValue(participationSubject);
        comp.textExercise = textExercise;

        fixture.detectChanges();
        tick();

        expect(comp.isAllowedToSubmitAfterDeadline).toBeTruthy();

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
        comp.submission = { id: 1, participation: { id: 1 } as Participation } as TextSubmission;
        comp.textExercise = { id: 1 } as TextExercise;
        comp.answer = 'abc';
        jest.spyOn(textSubmissionService, 'update');
        comp.submit();
        expect(textSubmissionService.update).toHaveBeenCalledOnce();
        expect(comp.isSaving).toBeFalsy();
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

    it('should destroy', () => {
        comp.submission = { text: 'abc' } as TextSubmission;
        comp.answer = 'def';
        comp.textExercise = { id: 1 } as TextExercise;
        jest.spyOn(textSubmissionService, 'update');
        comp.ngOnDestroy();
        expect(textSubmissionService.update).not.toHaveBeenCalled();
    });
});
