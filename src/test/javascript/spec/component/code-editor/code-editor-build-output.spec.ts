import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { DebugElement, SimpleChanges, SimpleChange } from '@angular/core';
import { LocalStorageService } from 'ngx-webstorage';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { AceEditorModule } from 'ng2-ace-editor';
import { stub, SinonStub } from 'sinon';
import { Observable, of } from 'rxjs';
import { CodeEditorBuildLogService, CodeEditorSessionService, CodeEditorBuildOutputComponent } from 'app/code-editor';
import { ArTEMiSTestModule } from '../../test.module';
import { Participation, ParticipationWebsocketService } from 'app/entities/participation';
import { MockCodeEditorBuildLogService } from '../../mocks/mock-code-editor-build-log.service';
import { SafeHtmlPipe } from 'app/shared';
import { MockCodeEditorSessionService, MockParticipationWebsocketService } from '../../mocks';
import { ResultService } from 'app/entities/result';
import { MockSyncStorage } from '../../mocks/mock-sync.storage';
import { Feedback } from 'app/entities/feedback';
import { MockResultService } from '../../mocks/mock-result.service';
import { BuildLogEntryArray } from 'app/entities/build-log';
import { AnnotationArray } from 'app/entities/ace-editor';

chai.use(sinonChai);
const expect = chai.expect;

describe('CodeEditorBuildOutputComponent', () => {
    let comp: CodeEditorBuildOutputComponent;
    let fixture: ComponentFixture<CodeEditorBuildOutputComponent>;
    let debugElement: DebugElement;
    let codeEditorBuildLogService: CodeEditorBuildLogService;
    let codeEditorSessionService: CodeEditorSessionService;
    let participationWebsocketService: ParticipationWebsocketService;
    let resultService: ResultService;
    let subscribeForLatestResultOfParticipationStub: SinonStub;
    let getBuildLogsStub: SinonStub;
    let getFeedbackDetailsForResultStub: SinonStub;
    let loadSessionStub: SinonStub;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArTEMiSTestModule, AceEditorModule],
            declarations: [CodeEditorBuildOutputComponent, SafeHtmlPipe],
            providers: [
                { provide: ResultService, useClass: MockResultService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: CodeEditorBuildLogService, useClass: MockCodeEditorBuildLogService },
                { provide: CodeEditorSessionService, useClass: MockCodeEditorSessionService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CodeEditorBuildOutputComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                codeEditorBuildLogService = debugElement.injector.get(CodeEditorBuildLogService);
                codeEditorSessionService = debugElement.injector.get(CodeEditorSessionService);
                participationWebsocketService = debugElement.injector.get(ParticipationWebsocketService);
                resultService = debugElement.injector.get(ResultService);
                subscribeForLatestResultOfParticipationStub = stub(participationWebsocketService, 'subscribeForLatestResultOfParticipation');
                getBuildLogsStub = stub(codeEditorBuildLogService, 'getBuildLogs');
                getFeedbackDetailsForResultStub = stub(resultService, 'getFeedbackDetailsForResult');
                loadSessionStub = stub(codeEditorSessionService, 'loadSession');
            });
    });

    afterEach(() => {
        subscribeForLatestResultOfParticipationStub.restore();
        getBuildLogsStub.restore();
        getFeedbackDetailsForResultStub.restore();
        loadSessionStub.restore();
    });

    it('should setup result websocket, fetch result details and build logs on participation change', () => {
        const result = { id: 1 };
        const participation = { id: 1, results: [result] } as Participation;
        const buildLogs = [
            {
                time: '2019-05-15T10:32:11+02:00',
                log: '[ERROR] COMPILATION ERROR : ',
            },
            {
                time: '2019-05-15T10:32:11+02:00',
                log:
                    '[ERROR] /var/atlassian/application-data/bamboo/xml-data/build-dir/COURSEPROGSHORT-BASE-JOB1/assignment/src/todo/main/BubbleSort.java:[8,12] cannot find symbol',
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
        const expectedBuildLogErrors = {
            timestamp: 1557909131000,
            errors: { 'src/todo/main/BubbleSort.java': new AnnotationArray(...[{ type: 'error', row: 7, column: 11, text: 'cannot find symbol', ts: 1557909131000 }]) },
        };
        subscribeForLatestResultOfParticipationStub.returns(Observable.of(null));
        getFeedbackDetailsForResultStub.returns(of({ body: [] }));
        getBuildLogsStub.returns(of(buildLogs));
        loadSessionStub.returns({ errors: {}, timestamp: 0 });

        comp.participation = participation;
        const changes: SimpleChanges = {
            participation: new SimpleChange(undefined, participation, true),
        };
        comp.ngOnChanges(changes);
        fixture.detectChanges();

        expect(getFeedbackDetailsForResultStub).to.have.been.calledOnceWithExactly(result.id);
        expect(loadSessionStub).to.have.been.calledOnceWithExactly();
        expect(getBuildLogsStub).to.have.been.calledOnce;
        expect(subscribeForLatestResultOfParticipationStub).to.have.been.calledOnceWithExactly(participation.id);
        expect(comp.rawBuildLogs).to.deep.equal(new BuildLogEntryArray(...buildLogs));
        expect(comp.buildLogErrors.errors).to.deep.equal(expectedBuildLogErrors.errors);
        expect(comp.buildLogErrors.timestamp).to.deep.equal(expectedBuildLogErrors.timestamp);

        const buildLogIsBuildingHtml = debugElement.query(By.css('.is-building'));
        expect(buildLogIsBuildingHtml).not.to.exist;
        const buildLogNoResultHtml = debugElement.query(By.css('.no-buildoutput'));
        expect(buildLogNoResultHtml).not.to.exist;
        const buildLogHtmlEntries = debugElement.queryAll(By.css('.build-output__entry'));
        expect(buildLogHtmlEntries).to.have.lengthOf(buildLogs.length);
    });

    it('should not retrieve build logs after participation change if result is successful (= all tests were passed)', () => {
        const result = { id: 1, successful: true };
        const participation = { id: 1, results: [result] } as Participation;
        comp.participation = participation;
        comp.participation = participation;
        subscribeForLatestResultOfParticipationStub.returns(Observable.of(null));
        getFeedbackDetailsForResultStub.returns(of({ ...result, feedbacks: [] }));
        const changes: SimpleChanges = {
            participation: new SimpleChange(undefined, participation, true),
        };
        comp.ngOnChanges(changes);
        fixture.detectChanges();
        fixture.detectChanges();
        expect(getFeedbackDetailsForResultStub).to.have.been.calledOnceWithExactly(result.id);
        expect(loadSessionStub).to.not.have.been.called;
        expect(getBuildLogsStub).to.not.have.been.called;
        expect(comp.rawBuildLogs).to.deep.equal(new BuildLogEntryArray());

        const buildLogIsBuildingHtml = debugElement.query(By.css('.is-building'));
        expect(buildLogIsBuildingHtml).not.to.exist;
        const buildLogNoResultHtml = debugElement.query(By.css('.no-buildoutput'));
        expect(buildLogNoResultHtml).to.exist;
        const buildLogHtmlEntries = debugElement.queryAll(By.css('.buildoutput__entry'));
        expect(buildLogHtmlEntries).to.have.lengthOf(0);
    });

    it('should not retrieve build logs after participation change if result is not successful but has feedback (= test case result details)', () => {
        const result = { id: 1, successful: false };
        const feedback = { id: 1 };
        const participation = { id: 1, results: [result] } as Participation;
        comp.participation = participation;

        subscribeForLatestResultOfParticipationStub.returns(Observable.of(null));
        getFeedbackDetailsForResultStub.returns(of({ body: [feedback] }));

        const changes: SimpleChanges = {
            participation: new SimpleChange(undefined, participation, true),
        };
        comp.ngOnChanges(changes);
        fixture.detectChanges();

        expect(getFeedbackDetailsForResultStub).to.have.been.calledOnceWithExactly(result.id);
        expect(loadSessionStub).to.not.have.been.called;
        expect(getBuildLogsStub).to.not.have.been.called;
        expect(comp.rawBuildLogs).to.deep.equal(new BuildLogEntryArray());
        expect(comp.buildLogErrors.errors).to.deep.equal({});

        const buildLogIsBuildingHtml = debugElement.query(By.css('.is-building'));
        expect(buildLogIsBuildingHtml).not.to.exist;
        const buildLogNoResultHtml = debugElement.query(By.css('.no-buildoutput'));
        expect(buildLogNoResultHtml).to.exist;
        const buildLogHtmlEntries = debugElement.queryAll(By.css('.buildoutput__entry'));
        expect(buildLogHtmlEntries).to.have.lengthOf(0);
    });

    it('should retrieve build logs if a non succesful result without feedbacks is emitted from result subscription', () => {
        const result = { id: 1, successful: false, feedbacks: [] as Feedback[] };
        const participation = { id: 1 } as Participation;

        const buildLogs = [
            {
                time: '2019-05-15T10:32:11+02:00',
                log: '[ERROR] COMPILATION ERROR : ',
            },
            {
                time: '2019-05-15T10:32:11+02:00',
                log:
                    '[ERROR] /var/atlassian/application-data/bamboo/xml-data/build-dir/COURSEPROGSHORT-BASE-JOB1/assignment/src/todo/main/BubbleSort.java:[8,12] cannot find symbol',
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
        const expectedBuildLogErrors = {
            timestamp: 1557909131000,
            errors: { 'src/todo/main/BubbleSort.java': new AnnotationArray(...[{ type: 'error', row: 7, column: 11, text: 'cannot find symbol', ts: 1557909131000 }]) },
        };
        getBuildLogsStub.returns(of(buildLogs));
        subscribeForLatestResultOfParticipationStub.returns(of(result));

        comp.participation = participation;
        const changes: SimpleChanges = {
            participation: new SimpleChange(undefined, participation, true),
        };
        comp.ngOnChanges(changes);
        fixture.detectChanges();

        expect(getBuildLogsStub).to.have.been.calledOnceWithExactly();
        expect(loadSessionStub).to.not.have.been.called;
        expect(getFeedbackDetailsForResultStub).to.not.have.been.called;
        expect(comp.rawBuildLogs).to.deep.equal(new BuildLogEntryArray(...buildLogs));
        expect(comp.buildLogErrors.errors).to.deep.equal(expectedBuildLogErrors.errors);
        expect(comp.buildLogErrors.timestamp).to.deep.equal(expectedBuildLogErrors.timestamp);

        const buildLogIsBuildingHtml = debugElement.query(By.css('.is-building'));
        expect(buildLogIsBuildingHtml).not.to.exist;
        const buildLogNoResultHtml = debugElement.query(By.css('.no-buildoutput'));
        expect(buildLogNoResultHtml).not.to.exist;
        const buildLogHtmlEntries = debugElement.queryAll(By.css('.build-output__entry'));
        expect(buildLogHtmlEntries).to.have.lengthOf(buildLogs.length);
    });
});
