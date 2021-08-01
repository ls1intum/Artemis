import * as sinon from 'sinon';
import { SinonStub, stub } from 'sinon';
import * as ace from 'brace';
import * as chai from 'chai';
import { DebugElement } from '@angular/core';
import * as moment from 'moment';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { AlertService } from 'app/core/util/alert.service';
import { ArtemisTestModule } from '../../test.module';
import { TranslateModule } from '@ngx-translate/core';
import { MockTextEditorService } from '../../helpers/mocks/service/mock-text-editor.service';
import * as sinonChai from 'sinon-chai';
import { TextEditorService } from 'app/exercises/text/participate/text-editor.service';
import { BehaviorSubject } from 'rxjs';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RouterTestingModule } from '@angular/router/testing';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockComponent, MockPipe } from 'ng-mocks';
import { TextResultComponent } from 'app/exercises/text/participate/text-result/text-result.component';
import { ComplaintInteractionsComponent } from 'app/complaints/complaint-interactions.component';
import { SubmissionResultStatusComponent } from 'app/overview/submission-result-status.component';
import { TextEditorComponent } from 'app/exercises/text/participate/text-editor.component';
import { textEditorRoute } from 'app/exercises/text/participate/text-editor.route';
import { TextExercise } from 'app/entities/text-exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ButtonComponent } from 'app/shared/components/button.component';
import { Result } from 'app/entities/result.model';
import { ComplaintsComponent } from 'app/complaints/complaints.component';
import { TextSubmission } from 'app/entities/text-submission.model';
import { ArtemisTeamModule } from 'app/exercises/shared/team/team.module';
import { ArtemisTeamSubmissionSyncModule } from 'app/exercises/shared/team-submission-sync/team-submission-sync.module';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { RatingModule } from 'app/exercises/shared/rating/rating.module';
import { TextSubmissionService } from 'app/exercises/text/participate/text-submission.service';
import { MockTextSubmissionService } from '../../helpers/mocks/service/mock-text-submission.service';
import { Language } from 'app/entities/tutor-group.model';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { Participation } from 'app/entities/participation/participation.model';
import { Exercise } from 'app/entities/exercise.model';
import { Submission } from 'app/entities/submission.model';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';

chai.use(sinonChai);
const expect = chai.expect;

describe('TextEditorComponent', () => {
    // needed to make sure ace is defined
    ace.acequire('ace/ext/modelist.js');

    let comp: TextEditorComponent;
    let fixture: ComponentFixture<TextEditorComponent>;
    let debugElement: DebugElement;
    let textService: TextEditorService;
    let textSubmissionService: TextSubmissionService;

    let getTextForParticipationStub: SinonStub;

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
        return TestBed.configureTestingModule({
            imports: [
                TranslateModule.forRoot(),
                ArtemisTestModule,
                ArtemisSharedModule,
                ArtemisTeamModule,
                ArtemisTeamSubmissionSyncModule,
                ArtemisHeaderExercisePageWithDetailsModule,
                RouterTestingModule.withRoutes([textEditorRoute[0]]),
                RatingModule,
            ],
            declarations: [
                TextEditorComponent,
                MockComponent(SubmissionResultStatusComponent),
                MockComponent(ButtonComponent),
                MockComponent(TextResultComponent),
                MockComponent(ComplaintsComponent),
                MockComponent(ComplaintInteractionsComponent),
                MockPipe(HtmlForMarkdownPipe),
            ],
            providers: [
                AlertService,
                { provide: ActivatedRoute, useValue: route },
                { provide: TextEditorService, useClass: MockTextEditorService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TextSubmissionService, useClass: MockTextSubmissionService },
            ],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TextEditorComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                textService = debugElement.injector.get(TextEditorService);
                textSubmissionService = TestBed.inject(TextSubmissionService);
                getTextForParticipationStub = stub(textService, 'get');
            });
    });

    afterEach(() => {
        getTextForParticipationStub.restore();
    });

    it('should not allow to submit after the deadline if there is no due date', fakeAsync(() => {
        const participationSubject = new BehaviorSubject<StudentParticipation>(participation);
        getTextForParticipationStub.returns(participationSubject);
        comp.textExercise = textExercise;

        fixture.detectChanges();
        tick();

        expect(comp.isAllowedToSubmitAfterDeadline).to.be.false;
        expect(comp.isAlwaysActive).to.be.true;

        tick();
        fixture.destroy();
        flush();
    }));

    it('should not allow to submit after the deadline if the initialization date is before the due date', fakeAsync(() => {
        participation.initializationDate = moment();
        textExercise.dueDate = moment().add(1, 'days');
        const participationSubject = new BehaviorSubject<StudentParticipation>(participation);
        getTextForParticipationStub.returns(participationSubject);
        comp.textExercise = textExercise;

        fixture.detectChanges();
        tick();

        expect(comp.isAllowedToSubmitAfterDeadline).to.be.false;

        tick();
        fixture.destroy();
        flush();
    }));

    it('should allow to submit after the deadline if the initilization date is after the due date', fakeAsync(() => {
        participation.initializationDate = moment().add(1, 'days');
        textExercise.dueDate = moment();
        const participationSubject = new BehaviorSubject<StudentParticipation>(participation);
        getTextForParticipationStub.returns(participationSubject);
        comp.textExercise = textExercise;

        fixture.detectChanges();
        tick();

        expect(comp.isAllowedToSubmitAfterDeadline).to.be.true;

        tick();
        fixture.destroy();
        flush();
    }));

    it('should not be always active if there is a result and no due date', fakeAsync(() => {
        const participationSubject = new BehaviorSubject<StudentParticipation>(participation);
        getTextForParticipationStub.returns(participationSubject);
        comp.result = result;
        comp.textExercise = textExercise;

        fixture.detectChanges();
        tick();

        expect(comp.isAlwaysActive).to.be.false;

        tick();
        fixture.destroy();
        flush();
    }));

    it('should be always active if there is no result and the initialization date is after the due date', fakeAsync(() => {
        const participationSubject = new BehaviorSubject<StudentParticipation>(participation);
        getTextForParticipationStub.returns(participationSubject);
        comp.textExercise = textExercise;
        comp.textExercise.dueDate = moment();
        participation.initializationDate = moment().add(1, 'days');

        fixture.detectChanges();
        tick();

        expect(comp.isAlwaysActive).to.be.true;

        tick();
        fixture.destroy();
        flush();
    }));

    it('should get inactive as soon as the due date passes the current date', fakeAsync(() => {
        const participationSubject = new BehaviorSubject<StudentParticipation>(participation);
        getTextForParticipationStub.returns(participationSubject);
        textExercise.dueDate = moment().add(1, 'days');
        participation.initializationDate = moment();

        fixture.detectChanges();
        tick();

        expect(comp.isActive).to.be.true;

        comp.textExercise.dueDate = moment().subtract(1, 'days');

        fixture.detectChanges();
        tick();

        expect(comp.isActive).to.be.false;

        tick();
        fixture.destroy();
        flush();
    }));

    it('should not submit while saving', () => {
        comp.isSaving = true;
        sinon.spy(textSubmissionService, 'update');
        comp.submit();
        expect(textSubmissionService.update).to.not.have.been.called;
    });

    it('should not submit without submission', () => {
        // @ts-ignore
        delete comp.submission;
        sinon.spy(textSubmissionService, 'update');
        comp.submit();
        expect(textSubmissionService.update).to.not.have.been.called;
    });

    it('should submit', () => {
        comp.submission = { id: 1, participation: { id: 1 } as Participation } as TextSubmission;
        comp.textExercise = { id: 1 } as TextExercise;
        comp.answer = 'abc';
        sinon.spy(textSubmissionService, 'update');
        comp.submit();
        expect(textSubmissionService.update).to.have.been.calledOnce;
        expect(comp.isSaving).to.be.false;
    });

    it('should return submission for answer', () => {
        sinon.spy(textService, 'predictLanguage');
        const submissionForAnswer = comp['submissionForAnswer']('abc');
        expect(submissionForAnswer.text).to.be.equal('abc');
        expect(submissionForAnswer.language).to.be.equal(Language.ENGLISH);
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
        expect(unreferencedFeedback?.length).to.be.equal(1);
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
        sinon.spy(comp, 'updateParticipation');
        comp.onReceiveSubmissionFromTeam(submission);
        expect(comp['updateParticipation']).to.have.been.calledOnce;
        expect(comp.answer).to.equal('abc');
    });

    it('should destroy', () => {
        comp.submission = { text: 'abc' } as TextSubmission;
        comp.answer = 'def';
        comp.textExercise = { id: 1 } as TextExercise;
        sinon.spy(textSubmissionService, 'update');
        comp.ngOnDestroy();
        expect(textSubmissionService.update).to.not.have.been.called;
    });
});
