import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { MockComponent } from 'ng-mocks';
import { TranslateModule } from '@ngx-translate/core';
import { WindowRef } from 'app/core';
import { DebugElement, SimpleChanges, SimpleChange } from '@angular/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { AceEditorModule } from 'ng2-ace-editor';
import { TreeviewItem, TreeviewModule } from 'ngx-treeview';
import { spy, stub, SinonStub } from 'sinon';
import { Observable, Subject } from 'rxjs';
import { CodeEditorBuildLogService, CodeEditorBuildOutputComponent, CodeEditorSessionService } from 'app/code-editor';
import { ArTEMiSTestModule } from '../../test.module';
import { Participation, ParticipationWebsocketService } from 'app/entities/participation';
import { MockCodeEditorBuildLogService } from '../../mocks/mock-code-editor-build-log.service';
import { SafeHtmlPipe } from 'app/shared';
import { MockCodeEditorSessionService, MockParticipationWebsocketService } from '../../mocks';
import { Result } from 'app/entities/result';

chai.use(sinonChai);
const expect = chai.expect;

describe('CodeEditorBuildOutputComponent', () => {
    let comp: CodeEditorBuildOutputComponent;
    let fixture: ComponentFixture<CodeEditorBuildOutputComponent>;
    let debugElement: DebugElement;
    let codeEditorBuildLogService: CodeEditorBuildLogService;
    let codeEditorSessionService: CodeEditorSessionService;
    let participationWebsocketService: ParticipationWebsocketService;
    let subscribeForLatestResultOfParticipationStub: SinonStub;
    let getBuildLogsStub: SinonStub;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArTEMiSTestModule, AceEditorModule],
            declarations: [CodeEditorBuildOutputComponent, SafeHtmlPipe],
            providers: [
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
                subscribeForLatestResultOfParticipationStub = stub(participationWebsocketService, 'subscribeForLatestResultOfParticipation');
                getBuildLogsStub = stub(codeEditorBuildLogService, 'getBuildLogs');
            });
    });

    afterEach(() => {
        subscribeForLatestResultOfParticipationStub.restore();
        getBuildLogsStub.restore();
    });

    it('should setup result websocket, fetch result details and build logs on participation change', () => {
        const participation = new Participation();
        participation.id = 1;
        participation.results = [{ id: 1 } as Result];
        subscribeForLatestResultOfParticipationStub.returns(Observable.of({ id: 2 }));

        comp.participation = participation;
        const changes: SimpleChanges = {
            participation: new SimpleChange(undefined, participation, true),
        };
        comp.ngOnChanges(changes);
        fixture.detectChanges();

        expect(subscribeForLatestResultOfParticipationStub).to.have.been.calledOnceWithExactly(participation.id);
    });
});
