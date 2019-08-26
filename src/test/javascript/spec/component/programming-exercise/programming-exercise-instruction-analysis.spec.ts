import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { MockComponent } from 'ng-mocks';
import { Subject } from 'rxjs';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { BrowserDynamicTestingModule } from '@angular/platform-browser-dynamic/testing';
import { DebugElement, SimpleChange, SimpleChanges } from '@angular/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { SinonSpy, SinonStub, spy, stub } from 'sinon';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTestModule } from '../../test.module';
import { Participation, ParticipationWebsocketService } from 'src/main/webapp/app/entities/participation';
import { Result, ResultService } from 'src/main/webapp/app/entities/result';
import { MockResultService } from '../../mocks/mock-result.service';
import { ProgrammingExercise, ProgrammingExerciseParticipationService, ProgrammingExerciseTestCaseService } from 'src/main/webapp/app/entities/programming-exercise';
import { MockParticipationWebsocketService } from '../../mocks';
import { MarkdownEditorComponent } from 'app/markdown-editor/markdown-editor.component';
import { MockProgrammingExerciseTestCaseService } from '../../mocks/mock-programming-exercise-test-case.service';
import { ArtemisProgrammingExerciseInstructionsEditorModule } from 'app/entities/programming-exercise/instructions/instructions-editor/programming-exercise-instructions-editor.module';
import {
    ProgrammingExerciseEditableInstructionComponent,
    ProgrammingExerciseInstructionAnalysisService,
    ProgrammingExerciseInstructionInstructorAnalysisComponent,
} from 'app/entities/programming-exercise/instructions/instructions-editor';
import { ArtemisProgrammingExerciseStatusModule } from 'app/entities/programming-exercise/status';
import { ExerciseHint } from 'app/entities/exercise-hint/exercise-hint.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('ProgrammingExerciseInstructionInstructorAnalysis', () => {
    let comp: ProgrammingExerciseInstructionInstructorAnalysisComponent;
    let fixture: ComponentFixture<ProgrammingExerciseInstructionInstructorAnalysisComponent>;
    let debugElement: DebugElement;

    let analysisService: ProgrammingExerciseInstructionAnalysisService;

    let analyzeProblemStatementStub: SinonStub;
    let emitAnalysisSpy: SinonSpy;

    const testCaseOkId = 'instruction_analysis_test-case-ok';
    const testCaseIssuesId = 'instruction_analysis_test-case-issues';

    const hintOkId = 'instruction_analysis_hint-ok';
    const hintIssuesId = 'instruction_analysis_hint-issues';

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, NgbModule, ArtemisProgrammingExerciseInstructionsEditorModule],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseInstructionInstructorAnalysisComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;

                analysisService = debugElement.injector.get(ProgrammingExerciseInstructionAnalysisService);

                analyzeProblemStatementStub = stub(analysisService, 'analyzeProblemStatement');
                emitAnalysisSpy = spy(comp.problemStatementAnalysis, 'emit');
            });
    });

    afterEach(() => {
        analyzeProblemStatementStub.restore();
        emitAnalysisSpy.restore();
    });

    it('should display the received analysis from the service', fakeAsync(() => {
        const completeAnalysis = {
            '0': { invalidTestCases: ['artemisApp.programmingExercise.testCaseAnalysis.invalidTestCase'] },
            '2': {
                invalidTestCases: ['artemisApp.programmingExercise.testCaseAnalysis.invalidTestCase'],
            },
        };
        const invalidTestCases = ['testMergeSort'];
        const invalidHints: string[] = [];
        const missingTestCases = ['testBubbleSort'];

        analyzeProblemStatementStub.returns({ completeAnalysis, invalidTestCases, invalidHints, missingTestCases });

        // dummy data.
        const problemStatement = 'lorem ipsum';
        const taskRegex = /abc/;
        const hints = [{ id: 3 }] as ExerciseHint[];
        const testCases = ['testabc'];
        comp.problemStatement = problemStatement;
        comp.taskRegex = taskRegex;
        comp.exerciseHints = hints;
        comp.exerciseTestCases = testCases;

        comp.ngOnInit();

        const changes: SimpleChanges = {
            problemStatement: new SimpleChange('dolet amat', problemStatement, false),
        };
        comp.ngOnChanges(changes);
        tick(500); // Update is debounced, otherwise we would send updates on every change.
        fixture.detectChanges();

        // Check internal state and output of component.
        expect(comp.missingTestCases).to.deep.equal(missingTestCases);
        expect(comp.invalidTestCases).to.deep.equal(invalidTestCases);
        expect(comp.invalidHints).to.deep.equal(invalidHints);
        expect(emitAnalysisSpy).to.have.been.calledOnceWithExactly(completeAnalysis);

        // Check rendered html.
        const testCaseOk = debugElement.query(By.css(`#${testCaseOkId}`));
        const testCaseIssues = debugElement.query(By.css(`#${testCaseIssuesId}`));
        const hintOk = debugElement.query(By.css(`#${hintOkId}`));
        const hintIssues = debugElement.query(By.css(`#${hintIssuesId}`));

        // Test cases are not ok according to the analysis.
        expect(testCaseOk).not.to.exist;
        expect(testCaseIssues).to.exist;

        // Hints are ok according to the analysis.
        expect(hintOk).to.exist;
        expect(hintIssues).not.to.exist;
    }));
});
