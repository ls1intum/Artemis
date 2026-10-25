import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { DebugElement } from '@angular/core';
import { Subject, of } from 'rxjs';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { CodeEditorBuildOutputComponent } from 'app/programming/manage/code-editor/build-output/code-editor-build-output.component';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { BuildLogEntryArray } from 'app/localci/shared/entities/build-log.model';
import { CodeEditorBuildLogService } from 'app/programming/shared/code-editor/services/code-editor-repository.service';
import { ResultService } from 'app/exercise/result/result.service';
import { MockResultService } from 'test/helpers/mocks/service/mock-result.service';
import { MockCodeEditorBuildLogService } from 'test/helpers/mocks/service/mock-code-editor-build-log.service';
import { MockParticipationWebsocketService } from 'test/helpers/mocks/service/mock-participation-websocket.service';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { StaticCodeAnalysisIssue } from 'app/programming/shared/entities/static-code-analysis-issue.model';
import { Feedback, FeedbackType, STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER } from 'app/assessment/shared/entities/feedback.model';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { MockPipe, MockProvider } from 'ng-mocks';
import { CodeEditorSubmissionService } from 'app/programming/shared/code-editor/services/code-editor-submission.service';
import { Annotation } from 'app/programming/shared/code-editor/monaco/code-editor-monaco.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';

describe('CodeEditorBuildOutputComponent', () => {
    let comp: CodeEditorBuildOutputComponent;
    let fixture: ComponentFixture<CodeEditorBuildOutputComponent>;
    let debugElement: DebugElement;
    let codeEditorBuildLogService: CodeEditorBuildLogService;
    let participationWebsocketService: ParticipationWebsocketService;
    let resultService: ResultService;
    let subscribeForLatestResultOfParticipationStub: ReturnType<typeof vi.spyOn>;
    let getBuildLogsStub: ReturnType<typeof vi.spyOn>;
    let getFeedbackDetailsForResultStub: ReturnType<typeof vi.spyOn>;

    const buildLogs = [
        {
            time: '2019-05-15T10:32:11+02:00',
            log: '[ERROR] COMPILATION ERROR : ',
        },
        {
            time: '2019-05-15T10:32:11+02:00',
            log: '[ERROR] /var/application-data/jenkins/xml-data/build-dir/COURSEPROGSHORT-BASE-JOB1/' + 'assignment/src/todo/main/BubbleSort.java:[8,12] cannot find symbol',
        },
        {
            time: '2019-05-15T10:32:11+02:00',
            log: '&nbsp; symbol:&nbsp; &nbsp;class voi',
        },
        {
            time: '2019-05-15T10:32:11+02:00',
            log: '&nbsp; location: class todo.main.BubbleSort',
        },
        {
            time: '2019-05-15T10:32:11+02:00',
            log: '[INFO] 1 error',
        },
    ];
    const expectedBuildLogErrors = [
        {
            fileName: 'src/todo/main/BubbleSort.java',
            type: 'error',
            row: 7,
            column: 11,
            text: 'cannot find symbol',
            timestamp: 1557909131000,
        },
    ];

    const staticCodeAnalysisIssue = {
        filePath: 'path',
        startLine: 2,
        endLine: 3,
        startColumn: 1,
        endColumn: 2,
        message: 'Issue',
        category: 'Misc',
        rule: 'Best rule',
        priority: '1',
    } as StaticCodeAnalysisIssue;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [CodeEditorBuildOutputComponent, MockPipe(ArtemisDatePipe)],
            providers: [
                { provide: ResultService, useClass: MockResultService },
                { provide: CodeEditorBuildLogService, useClass: MockCodeEditorBuildLogService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                MockProvider(CodeEditorSubmissionService),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        fixture = TestBed.createComponent(CodeEditorBuildOutputComponent);
        comp = fixture.componentInstance;
        debugElement = fixture.debugElement;
        codeEditorBuildLogService = TestBed.inject(CodeEditorBuildLogService);
        participationWebsocketService = TestBed.inject(ParticipationWebsocketService);
        resultService = TestBed.inject(ResultService);
        subscribeForLatestResultOfParticipationStub = vi.spyOn(participationWebsocketService, 'subscribeForLatestResultOfParticipation');
        getBuildLogsStub = vi.spyOn(codeEditorBuildLogService, 'getBuildLogs');
        getFeedbackDetailsForResultStub = vi.spyOn(resultService, 'getFeedbackDetailsForResult');
        vi.spyOn(TestBed.inject(CodeEditorSubmissionService), 'getBuildingState').mockReturnValue(of());
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('announces the owning grid collapse state after keyboard activation', () => {
        fixture.componentRef.setInput('participation', {});
        fixture.detectChanges();
        const header = fixture.nativeElement.querySelector('[role="button"]') as HTMLElement;
        const toggle = vi.spyOn(comp.onToggleCollapse, 'emit');
        expect(header.getAttribute('aria-expanded')).toBe('true');
        header.dispatchEvent(new KeyboardEvent('keyup', { key: ' ', bubbles: true }));
        expect(toggle).toHaveBeenCalledOnce();
        fixture.componentRef.setInput('collapsed', true);
        fixture.detectChanges();
        expect(header.getAttribute('aria-expanded')).toBe('false');
        fixture.componentRef.setInput('collapsed', false);
        fixture.detectChanges();
        expect(header.getAttribute('aria-expanded')).toBe('true');
    });

    it('should setup result websocket, fetch result details and build logs on participation change', () => {
        const result = { id: 1 };
        const submission = { id: 1, results: [result] };
        const participation = { id: 1, submissions: [submission] } as Participation;

        subscribeForLatestResultOfParticipationStub.mockReturnValue(of(null));
        getFeedbackDetailsForResultStub.mockReturnValue(of({ body: [] }));
        getBuildLogsStub.mockReturnValue(of(buildLogs));

        fixture.componentRef.setInput('participation', participation);
        fixture.detectChanges();

        expect(getFeedbackDetailsForResultStub).toHaveBeenCalledTimes(1);
        expect(getFeedbackDetailsForResultStub).toHaveBeenCalledWith(participation.id, result);
        expect(getBuildLogsStub).toHaveBeenCalledTimes(1);
        expect(subscribeForLatestResultOfParticipationStub).toHaveBeenCalledTimes(1);
        expect(subscribeForLatestResultOfParticipationStub).toHaveBeenCalledWith(participation.id, true);
        expect(comp.rawBuildLogs()).toStrictEqual(BuildLogEntryArray.fromBuildLogs(buildLogs));
        const extractedErrors = comp.rawBuildLogs().extractErrors(ProgrammingLanguage.JAVA, ProjectType.PLAIN_MAVEN);
        expect(extractedErrors).toHaveLength(expectedBuildLogErrors.length);
        expect(extractedErrors).toEqual(expect.arrayContaining(expectedBuildLogErrors));

        const buildLogIsBuildingHtml = debugElement.query(By.css('.is-building'));
        expect(buildLogIsBuildingHtml).toBeNull();
        const buildLogNoResultHtml = debugElement.query(By.css('.no-buildoutput'));
        expect(buildLogNoResultHtml).toBeNull();
        const buildLogHtmlEntries = debugElement.queryAll(By.css('.build-output__entry'));
        expect(buildLogHtmlEntries).toHaveLength(buildLogs.length);
    });

    it('does not re-fetch build logs when the participation object is replaced with the same id and latest result (prevents the constructor-effect buildlogs loop, #12976)', () => {
        const makeParticipation = (resultId: number) => ({ id: 1, submissions: [{ id: 1, results: [{ id: resultId }] }] }) as Participation;
        subscribeForLatestResultOfParticipationStub.mockReturnValue(of(null));
        getFeedbackDetailsForResultStub.mockReturnValue(of({ body: [] }));
        getBuildLogsStub.mockReturnValue(of(buildLogs));

        fixture.componentRef.setInput('participation', makeParticipation(5));
        fixture.detectChanges();
        expect(getBuildLogsStub).toHaveBeenCalledTimes(1);

        getBuildLogsStub.mockClear();
        // Replace the participation with a NEW object carrying the same id and same latest result id → must NOT re-fetch,
        // otherwise the constructor effect re-fetches build logs on every change-detection cycle in an infinite loop.
        fixture.componentRef.setInput('participation', makeParticipation(5));
        fixture.detectChanges();
        expect(getBuildLogsStub).not.toHaveBeenCalled();

        // A genuinely new result (different result id) re-fetches.
        fixture.componentRef.setInput('participation', makeParticipation(6));
        fixture.detectChanges();
        expect(getBuildLogsStub).toHaveBeenCalledTimes(1);
    });

    it('should not retrieve build logs after participation change, if no result is available', () => {
        const participation = { id: 1 } as Participation;
        subscribeForLatestResultOfParticipationStub.mockReturnValue(of(null));
        fixture.componentRef.setInput('participation', participation);
        fixture.detectChanges();
        expect(getBuildLogsStub).not.toHaveBeenCalled();
        expect(comp.rawBuildLogs()).toStrictEqual(new BuildLogEntryArray());

        const buildLogIsBuildingHtml = debugElement.query(By.css('.is-building'));
        expect(buildLogIsBuildingHtml).toBeNull();
        const buildLogNoResultHtml = debugElement.query(By.css('.no-buildoutput'));
        expect(buildLogNoResultHtml).not.toBeNull();
        expect(buildLogNoResultHtml.nativeElement.textContent).toContain('artemisApp.editor.noBuildOutput');
        const buildLogHtmlEntries = debugElement.queryAll(By.css('.buildoutput__entry'));
        expect(buildLogHtmlEntries).toHaveLength(0);
    });

    it.each([
        { language: ProgrammingLanguage.C, successful: true },
        { language: ProgrammingLanguage.C, successful: false },
        { language: ProgrammingLanguage.JAVA, successful: true },
        { language: ProgrammingLanguage.JAVA, successful: false },
    ])('explains why output is unavailable after compiling $language with test success $successful', ({ language, successful }) => {
        const submission = new ProgrammingSubmission();
        submission.id = 1;
        submission.buildFailed = false;
        const result = new Result();
        result.id = 1;
        result.successful = successful;
        result.assessmentType = AssessmentType.AUTOMATIC;
        result.submission = submission;
        result.feedbacks = [];
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.programmingLanguage = language;
        const participation = new ProgrammingExerciseStudentParticipation();
        participation.id = 1;
        participation.exercise = exercise;
        subscribeForLatestResultOfParticipationStub.mockReturnValue(of(result));
        fixture.componentRef.setInput('participation', participation);

        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).toContain('artemisApp.editor.buildOutputOnlyOnFailure');
        expect(fixture.nativeElement.textContent).not.toContain('artemisApp.editor.noBuildOutput');
        expect(getBuildLogsStub).not.toHaveBeenCalled();
    });

    it.each([
        { language: ProgrammingLanguage.C, successful: true, buildFailed: false },
        { language: ProgrammingLanguage.C, successful: false, buildFailed: false },
        { language: ProgrammingLanguage.JAVA, successful: true, buildFailed: false },
        { language: ProgrammingLanguage.JAVA, successful: false, buildFailed: false },
        { language: ProgrammingLanguage.C, successful: false, buildFailed: true },
        { language: ProgrammingLanguage.JAVA, successful: false, buildFailed: true },
    ])('resolves cold-load $language output from the owning submission: successful=$successful, buildFailed=$buildFailed', ({ language, successful, buildFailed }) => {
        // The student editor DTO has results inside submissions, without a result.submission back-reference.
        const result: Result = { id: 6, successful, assessmentType: AssessmentType.AUTOMATIC, feedbacks: [] };
        const submission: ProgrammingSubmission = { id: 6, buildFailed, results: [result] };
        const participation: Participation = { id: 6, exercise: { programmingLanguage: language } as ProgrammingExercise, submissions: [submission] };
        subscribeForLatestResultOfParticipationStub.mockReturnValue(of(null));
        getBuildLogsStub.mockReturnValue(of(buildLogs));
        fixture.componentRef.setInput('participation', participation);

        fixture.detectChanges();

        if (buildFailed) {
            expect(getBuildLogsStub).toHaveBeenCalledOnce();
            expect(fixture.nativeElement.textContent).toContain('cannot find symbol');
            expect(fixture.nativeElement.textContent).not.toContain('artemisApp.editor.buildOutputOnlyOnFailure');
        } else {
            expect(fixture.nativeElement.textContent).toContain('artemisApp.editor.buildOutputOnlyOnFailure');
            expect(fixture.nativeElement.textContent).not.toContain('artemisApp.editor.noBuildOutput');
            expect(getBuildLogsStub).not.toHaveBeenCalled();
        }
    });

    it('matches a detached historical result by ID instead of using the latest submission', () => {
        const historical: Result = { id: 6, feedbacks: [], assessmentType: AssessmentType.AUTOMATIC };
        const latest: Result = { id: 7, feedbacks: [], assessmentType: AssessmentType.AUTOMATIC };
        const websocket = new Subject<Result>();
        subscribeForLatestResultOfParticipationStub.mockReturnValue(websocket.asObservable());
        getBuildLogsStub.mockReturnValue(of(buildLogs));
        fixture.componentRef.setInput('participation', {
            id: 6,
            submissions: [
                { id: 6, buildFailed: true, results: [historical] },
                { id: 7, buildFailed: false, results: [latest] },
            ],
        });
        fixture.detectChanges();
        expect(getBuildLogsStub).not.toHaveBeenCalled();

        websocket.next({ ...historical });
        fixture.detectChanges();

        expect(getBuildLogsStub).toHaveBeenCalledOnce();
        expect(fixture.nativeElement.textContent).toContain('cannot find symbol');
        expect(fixture.nativeElement.textContent).not.toContain('artemisApp.editor.buildOutputOnlyOnFailure');
    });

    it.each([false, true])('preserves generic unknown-build-state log fetching with explicit back-reference %s', (hasBackReference) => {
        const result: Result = { id: 6, feedbacks: [], assessmentType: AssessmentType.AUTOMATIC };
        const submission = { id: 6, results: [result] };
        if (hasBackReference) {
            result.submission = submission;
        }
        subscribeForLatestResultOfParticipationStub.mockReturnValue(of(null));
        getBuildLogsStub.mockReturnValue(of([]));
        fixture.componentRef.setInput('participation', { id: 6, submissions: [submission] });

        fixture.detectChanges();

        expect(getBuildLogsStub).toHaveBeenCalledTimes(hasBackReference ? 0 : 1);
        expect(fixture.nativeElement.textContent).toContain('artemisApp.editor.noBuildOutput');
        expect(fixture.nativeElement.textContent).not.toContain('artemisApp.editor.buildOutputOnlyOnFailure');
    });

    it('matches unsaved results only by object identity, without treating undefined IDs as equal', () => {
        const ownedResult: Result = { feedbacks: [], assessmentType: AssessmentType.AUTOMATIC };
        const websocket = new Subject<Result>();
        subscribeForLatestResultOfParticipationStub.mockReturnValue(websocket.asObservable());
        getBuildLogsStub.mockReturnValue(of([]));
        fixture.componentRef.setInput('participation', { id: 6, submissions: [{ buildFailed: false, results: [ownedResult] }] });
        fixture.detectChanges();
        expect(getBuildLogsStub).not.toHaveBeenCalled();

        websocket.next({ feedbacks: [], assessmentType: AssessmentType.AUTOMATIC });
        fixture.detectChanges();

        expect(getBuildLogsStub).toHaveBeenCalledOnce();
        expect(fixture.nativeElement.textContent).toContain('artemisApp.editor.noBuildOutput');
        expect(fixture.nativeElement.textContent).not.toContain('artemisApp.editor.buildOutputOnlyOnFailure');
    });

    it('prefers an explicit result submission over a different participation owner state', () => {
        const result: Result = { id: 6, feedbacks: [], assessmentType: AssessmentType.AUTOMATIC };
        const websocket = new Subject<Result>();
        subscribeForLatestResultOfParticipationStub.mockReturnValue(websocket.asObservable());
        getBuildLogsStub.mockReturnValue(of(buildLogs));
        fixture.componentRef.setInput('participation', { id: 6, submissions: [{ buildFailed: true, results: [result] }] });
        fixture.detectChanges();
        getBuildLogsStub.mockClear();

        websocket.next({ ...result, submission: { buildFailed: false } as ProgrammingSubmission });
        fixture.detectChanges();

        expect(getBuildLogsStub).not.toHaveBeenCalled();
        expect(fixture.nativeElement.textContent).toContain('artemisApp.editor.buildOutputOnlyOnFailure');
    });

    it('keeps the unavailable-output message when a failed build has no stored logs', () => {
        const result = { id: 1, submission: { buildFailed: true }, feedbacks: [] } as Result;
        subscribeForLatestResultOfParticipationStub.mockReturnValue(of(result));
        getBuildLogsStub.mockReturnValue(of([]));
        fixture.componentRef.setInput('participation', { id: 1 });

        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).toContain('artemisApp.editor.noBuildOutput');
        expect(fixture.nativeElement.textContent).not.toContain('artemisApp.editor.buildOutputOnlyOnFailure');
        expect(getBuildLogsStub).toHaveBeenCalledOnce();
    });

    it('does not describe an Athena assessment as a completed build', () => {
        const result = { id: 1, assessmentType: AssessmentType.AUTOMATIC_ATHENA, submission: { buildFailed: false }, feedbacks: [] } as Result;
        subscribeForLatestResultOfParticipationStub.mockReturnValue(of(result));
        fixture.componentRef.setInput('participation', { id: 1 });

        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).toContain('artemisApp.editor.noBuildOutput');
        expect(fixture.nativeElement.textContent).not.toContain('artemisApp.editor.buildOutputOnlyOnFailure');
        expect(getBuildLogsStub).not.toHaveBeenCalled();
    });

    it('shows building progress instead of an empty-state explanation while the next build runs', () => {
        const result = { id: 1, submission: { buildFailed: false }, feedbacks: [] } as Result;
        subscribeForLatestResultOfParticipationStub.mockReturnValue(of(result));
        fixture.componentRef.setInput('participation', { id: 1 });
        fixture.detectChanges();

        comp.isBuilding.set(true);
        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).toContain('artemisApp.editor.building');
        expect(fixture.nativeElement.textContent).not.toContain('artemisApp.editor.buildOutputOnlyOnFailure');
        expect(fixture.nativeElement.textContent).not.toContain('artemisApp.editor.noBuildOutput');
    });

    it('should not retrieve build logs after participation change, if submission could be built', () => {
        const result = { id: 1, successful: true } as Result;
        const submission = { id: 1, buildFailed: false, results: [result] } as ProgrammingSubmission;
        result.submission = submission;
        const participation = { id: 1, submissions: [submission] } as Participation;
        subscribeForLatestResultOfParticipationStub.mockReturnValue(of(null));
        getFeedbackDetailsForResultStub.mockReturnValue(of({ ...result, feedbacks: [] }));
        fixture.componentRef.setInput('participation', participation);
        fixture.detectChanges();
        expect(getFeedbackDetailsForResultStub).toHaveBeenCalledTimes(1);
        expect(getFeedbackDetailsForResultStub).toHaveBeenCalledWith(participation.id!, result);
        expect(getBuildLogsStub).not.toHaveBeenCalled();
        expect(comp.rawBuildLogs()).toStrictEqual(new BuildLogEntryArray());

        const buildLogIsBuildingHtml = debugElement.query(By.css('.is-building'));
        expect(buildLogIsBuildingHtml).toBeNull();
        const buildLogNoResultHtml = debugElement.query(By.css('.no-buildoutput'));
        expect(buildLogNoResultHtml).not.toBeNull();
        const buildLogHtmlEntries = debugElement.queryAll(By.css('.buildoutput__entry'));
        expect(buildLogHtmlEntries).toHaveLength(0);
    });

    it('should retrieve build logs if no result submission is available', () => {
        const result = { id: 1, successful: false };
        const participation = { id: 1 } as Participation;

        getBuildLogsStub.mockReturnValue(of(buildLogs));
        subscribeForLatestResultOfParticipationStub.mockReturnValue(of(result));

        fixture.componentRef.setInput('participation', participation);
        fixture.detectChanges();

        expect(getBuildLogsStub).toHaveBeenCalledTimes(1);
        expect(getBuildLogsStub).toHaveBeenCalledWith();
        expect(getFeedbackDetailsForResultStub).not.toHaveBeenCalled();
        expect(comp.rawBuildLogs()).toStrictEqual(BuildLogEntryArray.fromBuildLogs(buildLogs));
        const extractedErrors = comp.rawBuildLogs().extractErrors(ProgrammingLanguage.JAVA, ProjectType.PLAIN_MAVEN);
        expect(extractedErrors).toHaveLength(expectedBuildLogErrors.length);
        expect(extractedErrors).toEqual(expect.arrayContaining(expectedBuildLogErrors));

        const buildLogIsBuildingHtml = debugElement.query(By.css('.is-building'));
        expect(buildLogIsBuildingHtml).toBeNull();
        const buildLogNoResultHtml = debugElement.query(By.css('.no-buildoutput'));
        expect(buildLogNoResultHtml).toBeNull();
        const buildLogHtmlEntries = debugElement.queryAll(By.css('.build-output__entry'));
        expect(buildLogHtmlEntries).toHaveLength(buildLogs.length);
    });

    it('should fetch build logs only once when the result websocket re-emits the same result (prevents the buildlogs request flood)', () => {
        // Regression test for the runaway `buildlogs` request loop: the per-participation result BehaviorSubject can
        // re-emit the same result many times; without de-duplication the build-output component fired a new buildlogs
        // request on every emission (switchMap cancelling each in-flight request), flooding the server and pegging the
        // client until it crashed. The same result must therefore trigger at most one fetch.
        const result = { id: 1, successful: false } as Result;
        const participation = { id: 1 } as Participation;

        getBuildLogsStub.mockReturnValue(of(buildLogs));
        // The subject emits the identical result three times in a row.
        subscribeForLatestResultOfParticipationStub.mockReturnValue(of(result, result, result));

        fixture.componentRef.setInput('participation', participation);
        fixture.detectChanges();

        expect(getBuildLogsStub).toHaveBeenCalledTimes(1);
    });

    it('should retrieve build logs if result submission could not be built', () => {
        const submission = { id: 1, buildFailed: true } as ProgrammingSubmission;
        const result = { id: 1, successful: true } as Result;
        result.submission = submission;
        const participation = { id: 1 } as Participation;

        getBuildLogsStub.mockReturnValue(of(buildLogs));
        subscribeForLatestResultOfParticipationStub.mockReturnValue(of(result));

        fixture.componentRef.setInput('participation', participation);
        fixture.detectChanges();

        expect(getBuildLogsStub).toHaveBeenCalledTimes(1);
        expect(getBuildLogsStub).toHaveBeenCalledWith();
        expect(getFeedbackDetailsForResultStub).not.toHaveBeenCalled();
        expect(comp.rawBuildLogs()).toStrictEqual(BuildLogEntryArray.fromBuildLogs(buildLogs));
        const extractedErrors = comp.rawBuildLogs().extractErrors(ProgrammingLanguage.JAVA, ProjectType.PLAIN_MAVEN);
        expect(extractedErrors).toHaveLength(expectedBuildLogErrors.length);
        expect(extractedErrors).toEqual(expect.arrayContaining(expectedBuildLogErrors));

        const buildLogIsBuildingHtml = debugElement.query(By.css('.is-building'));
        expect(buildLogIsBuildingHtml).toBeNull();
        const buildLogNoResultHtml = debugElement.query(By.css('.no-buildoutput'));
        expect(buildLogNoResultHtml).toBeNull();
        const buildLogHtmlEntries = debugElement.queryAll(By.css('.build-output__entry'));
        expect(buildLogHtmlEntries).toHaveLength(buildLogs.length);
    });

    it('should create annotation from static code analysis feedback', () => {
        const submission = { id: 1, buildFailed: false } as ProgrammingSubmission;
        const result = { id: 1, successful: true } as Result;
        result.submission = submission;
        const participation = { id: 1, submissions: [submission] } as Participation;
        submission.participation = participation;
        submission.results = [result];
        const feedback = {
            id: 1,
            type: FeedbackType.AUTOMATIC,
            text: STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER,
            detailText: JSON.stringify(staticCodeAnalysisIssue),
        } as Feedback;
        subscribeForLatestResultOfParticipationStub.mockReturnValue(of(null));
        getFeedbackDetailsForResultStub.mockReturnValue(of({ body: [feedback] }));
        let emittedAnnotations: Annotation[] = [];
        comp.onAnnotations.subscribe((emitted: any) => {
            emittedAnnotations = emitted;
        });
        fixture.componentRef.setInput('participation', participation);
        fixture.detectChanges();

        expect(emittedAnnotations).toHaveLength(1);
        const annotation = emittedAnnotations[0];
        expect(annotation.fileName).toBe(staticCodeAnalysisIssue.filePath);
        expect(annotation.row).toBe(staticCodeAnalysisIssue.startLine - 1);
        expect(annotation.column).toBe(staticCodeAnalysisIssue.startColumn! - 1);
    });
});
