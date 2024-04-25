import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { DebugElement } from '@angular/core';
import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { of } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { triggerChanges } from '../../helpers/utils/general.utils';
import { CodeEditorBuildOutputComponent } from 'app/exercises/programming/shared/code-editor/build-output/code-editor-build-output.component';
import { Participation } from 'app/entities/participation/participation.model';
import { BuildLogEntryArray } from 'app/entities/build-log.model';
import { CodeEditorBuildLogService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { MockResultService } from '../../helpers/mocks/service/mock-result.service';
import { MockCodeEditorBuildLogService } from '../../helpers/mocks/service/mock-code-editor-build-log.service';
import { MockParticipationWebsocketService } from '../../helpers/mocks/service/mock-participation-websocket.service';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { Result } from 'app/entities/result.model';
import { StaticCodeAnalysisIssue } from 'app/entities/static-code-analysis-issue.model';
import { Feedback, FeedbackType, STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER } from 'app/entities/feedback.model';
import { Annotation } from 'app/exercises/programming/shared/code-editor/ace/code-editor-ace.component';
import { ProgrammingLanguage, ProjectType } from 'app/entities/programming-exercise.model';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockPipe, MockProvider } from 'ng-mocks';
import { CodeEditorSubmissionService } from 'app/exercises/programming/shared/code-editor/service/code-editor-submission.service';

describe('CodeEditorBuildOutputComponent', () => {
    let comp: CodeEditorBuildOutputComponent;
    let fixture: ComponentFixture<CodeEditorBuildOutputComponent>;
    let debugElement: DebugElement;
    let codeEditorBuildLogService: CodeEditorBuildLogService;
    let participationWebsocketService: ParticipationWebsocketService;
    let resultService: ResultService;
    let subscribeForLatestResultOfParticipationStub: jest.SpyInstance;
    let getBuildLogsStub: jest.SpyInstance;
    let getFeedbackDetailsForResultStub: jest.SpyInstance;

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
            imports: [ArtemisTestModule, AceEditorModule],
            declarations: [CodeEditorBuildOutputComponent, MockPipe(ArtemisDatePipe)],
            providers: [
                { provide: ResultService, useClass: MockResultService },
                { provide: CodeEditorBuildLogService, useClass: MockCodeEditorBuildLogService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                MockProvider(CodeEditorSubmissionService),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CodeEditorBuildOutputComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                codeEditorBuildLogService = TestBed.inject(CodeEditorBuildLogService);
                participationWebsocketService = TestBed.inject(ParticipationWebsocketService);
                resultService = TestBed.inject(ResultService);
                subscribeForLatestResultOfParticipationStub = jest.spyOn(participationWebsocketService, 'subscribeForLatestResultOfParticipation');
                getBuildLogsStub = jest.spyOn(codeEditorBuildLogService, 'getBuildLogs');
                getFeedbackDetailsForResultStub = jest.spyOn(resultService, 'getFeedbackDetailsForResult');
                jest.spyOn(TestBed.inject(CodeEditorSubmissionService), 'getBuildingState').mockReturnValue(of());
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should setup result websocket, fetch result details and build logs on participation change', () => {
        const result = { id: 1 };
        const participation = { id: 1, results: [result] } as Participation;

        subscribeForLatestResultOfParticipationStub.mockReturnValue(of(null));
        getFeedbackDetailsForResultStub.mockReturnValue(of({ body: [] }));
        getBuildLogsStub.mockReturnValue(of(buildLogs));

        comp.participation = participation;
        triggerChanges(comp, { property: 'participation', currentValue: participation });
        fixture.detectChanges();

        expect(getFeedbackDetailsForResultStub).toHaveBeenCalledOnce();
        expect(getFeedbackDetailsForResultStub).toHaveBeenCalledWith(participation.id, result);
        expect(getBuildLogsStub).toHaveBeenCalledOnce();
        expect(subscribeForLatestResultOfParticipationStub).toHaveBeenCalledOnce();
        expect(subscribeForLatestResultOfParticipationStub).toHaveBeenCalledWith(participation.id, true);
        expect(comp.rawBuildLogs).toStrictEqual(BuildLogEntryArray.fromBuildLogs(buildLogs));
        expect(comp.rawBuildLogs.extractErrors(ProgrammingLanguage.JAVA, ProjectType.PLAIN_MAVEN)).toIncludeSameMembers(expectedBuildLogErrors);

        const buildLogIsBuildingHtml = debugElement.query(By.css('.is-building'));
        expect(buildLogIsBuildingHtml).toBeNull();
        const buildLogNoResultHtml = debugElement.query(By.css('.no-buildoutput'));
        expect(buildLogNoResultHtml).toBeNull();
        const buildLogHtmlEntries = debugElement.queryAll(By.css('.build-output__entry'));
        expect(buildLogHtmlEntries).toHaveLength(buildLogs.length);
    });

    it('should not retrieve build logs after participation change, if no result is available', () => {
        const participation = { id: 1 } as Participation;
        comp.participation = participation;
        subscribeForLatestResultOfParticipationStub.mockReturnValue(of(null));
        triggerChanges(comp, { property: 'participation', currentValue: participation });
        fixture.detectChanges();
        expect(getBuildLogsStub).not.toHaveBeenCalled();
        expect(comp.rawBuildLogs).toStrictEqual(new BuildLogEntryArray());

        const buildLogIsBuildingHtml = debugElement.query(By.css('.is-building'));
        expect(buildLogIsBuildingHtml).toBeNull();
        const buildLogNoResultHtml = debugElement.query(By.css('.no-buildoutput'));
        expect(buildLogNoResultHtml).not.toBeNull();
        const buildLogHtmlEntries = debugElement.queryAll(By.css('.buildoutput__entry'));
        expect(buildLogHtmlEntries).toHaveLength(0);
    });

    it('should not retrieve build logs after participation change, if submission could be built', () => {
        const submission = { id: 1, buildFailed: false } as ProgrammingSubmission;
        const result = { id: 1, successful: true } as Result;
        result.submission = submission;
        const participation = { id: 1, results: [result] } as Participation;
        comp.participation = participation;
        subscribeForLatestResultOfParticipationStub.mockReturnValue(of(null));
        getFeedbackDetailsForResultStub.mockReturnValue(of({ ...result, feedbacks: [] }));
        triggerChanges(comp, { property: 'participation', currentValue: participation });
        fixture.detectChanges();
        expect(getFeedbackDetailsForResultStub).toHaveBeenCalledOnce();
        expect(getFeedbackDetailsForResultStub).toHaveBeenCalledWith(participation.id!, result);
        expect(getBuildLogsStub).not.toHaveBeenCalled();
        expect(comp.rawBuildLogs).toStrictEqual(new BuildLogEntryArray());

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

        comp.participation = participation;
        triggerChanges(comp, { property: 'participation', currentValue: participation });
        fixture.detectChanges();

        expect(getBuildLogsStub).toHaveBeenCalledOnce();
        expect(getBuildLogsStub).toHaveBeenCalledWith();
        expect(getFeedbackDetailsForResultStub).not.toHaveBeenCalled();
        expect(comp.rawBuildLogs).toStrictEqual(BuildLogEntryArray.fromBuildLogs(buildLogs));
        expect(comp.rawBuildLogs.extractErrors(ProgrammingLanguage.JAVA, ProjectType.PLAIN_MAVEN)).toIncludeSameMembers(expectedBuildLogErrors);

        const buildLogIsBuildingHtml = debugElement.query(By.css('.is-building'));
        expect(buildLogIsBuildingHtml).toBeNull();
        const buildLogNoResultHtml = debugElement.query(By.css('.no-buildoutput'));
        expect(buildLogNoResultHtml).toBeNull();
        const buildLogHtmlEntries = debugElement.queryAll(By.css('.build-output__entry'));
        expect(buildLogHtmlEntries).toHaveLength(buildLogs.length);
    });

    it('should retrieve build logs if result submission could not be built', () => {
        const submission = { id: 1, buildFailed: true } as ProgrammingSubmission;
        const result = { id: 1, successful: true } as Result;
        result.submission = submission;
        const participation = { id: 1 } as Participation;

        getBuildLogsStub.mockReturnValue(of(buildLogs));
        subscribeForLatestResultOfParticipationStub.mockReturnValue(of(result));

        comp.participation = participation;
        triggerChanges(comp, { property: 'participation', currentValue: participation });
        fixture.detectChanges();

        expect(getBuildLogsStub).toHaveBeenCalledOnce();
        expect(getBuildLogsStub).toHaveBeenCalledWith();
        expect(getFeedbackDetailsForResultStub).not.toHaveBeenCalled();
        expect(comp.rawBuildLogs).toStrictEqual(BuildLogEntryArray.fromBuildLogs(buildLogs));
        expect(comp.rawBuildLogs.extractErrors(ProgrammingLanguage.JAVA, ProjectType.PLAIN_MAVEN)).toIncludeSameMembers(expectedBuildLogErrors);

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
        const participation = { id: 1, results: [result] } as Participation;
        comp.participation = participation;
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
        triggerChanges(comp, { property: 'participation', currentValue: participation });

        expect(emittedAnnotations).toHaveLength(1);
        const annotation = emittedAnnotations[0];
        expect(annotation.fileName).toBe(staticCodeAnalysisIssue.filePath);
        expect(annotation.row).toBe(staticCodeAnalysisIssue.startLine - 1);
        expect(annotation.column).toBe(staticCodeAnalysisIssue.startColumn! - 1);
    });
});
