import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { Observable, of } from 'rxjs';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { DebugElement } from '@angular/core';
import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { SinonSpy, SinonStub, spy, stub } from 'sinon';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisProgrammingExerciseInstructionsEditorModule } from 'app/exercises/programming/manage/instructions-editor/programming-exercise-instructions-editor.module';
import { ExerciseHint } from 'app/entities/exercise-hint.model';
import { TaskCommand } from 'app/shared/markdown-editor/domainCommands/programming-exercise/task.command';
import { HttpResponse } from '@angular/common/http';
import { triggerChanges } from '../../helpers/utils/general.utils';
import { ExerciseHintService, IExerciseHintService } from 'app/exercises/shared/exercise-hint/manage/exercise-hint.service';
import { ProgrammingExerciseInstructionAnalysisComponent } from 'app/exercises/programming/manage/instructions-editor/analysis/programming-exercise-instruction-analysis.component';
import { ProgrammingExerciseInstructionAnalysisService } from 'app/exercises/programming/manage/instructions-editor/analysis/programming-exercise-instruction-analysis.service';
import { MockModule } from 'ng-mocks';
import { ClipboardModule } from 'ngx-clipboard';

chai.use(sinonChai);
const expect = chai.expect;

describe('ProgrammingExerciseInstructionInstructorAnalysis', () => {
    let comp: ProgrammingExerciseInstructionAnalysisComponent;
    let fixture: ComponentFixture<ProgrammingExerciseInstructionAnalysisComponent>;
    let debugElement: DebugElement;

    const testCaseOkId = 'instruction_analysis_test-case-ok';
    const testCaseIssuesId = 'instruction_analysis_test-case-issues';

    const hintOkId = 'instruction_analysis_hint-ok';
    const hintIssuesId = 'instruction_analysis_hint-issues';

    const taskCommand = new TaskCommand();
    const taskRegex = taskCommand.getTagRegex('g');
    const exerciseTestCases = ['test1', 'test2', 'test6', 'test7'];
    const problemStatement =
        '1. [task][SortStrategy Interface](test1,test2) \n 2. [task][SortStrategy Interface](test3) \n lorem ipsum \n lorem \n  3. [task][SortStrategy Interface](test2,test4)';

    const exerciseHints = [{ id: 1 }, { id: 2 }] as ExerciseHint[];

    describe('Component tests', () => {
        let analysisService: ProgrammingExerciseInstructionAnalysisService;

        let analyzeProblemStatementStub: SinonStub;
        let emitAnalysisSpy: SinonSpy;

        beforeEach(async () => {
            return TestBed.configureTestingModule({
                imports: [TranslateModule.forRoot(), ArtemisTestModule, MockModule(NgbModule), ArtemisProgrammingExerciseInstructionsEditorModule, MockModule(ClipboardModule)],
            })
                .compileComponents()
                .then(() => {
                    fixture = TestBed.createComponent(ProgrammingExerciseInstructionAnalysisComponent);
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
            const hints = [{ id: 3 }] as ExerciseHint[];
            const testCases = ['testabc'];
            comp.problemStatement = problemStatement;
            comp.taskRegex = taskRegex;
            comp.exerciseHints = hints;
            comp.exerciseTestCases = testCases;

            comp.ngOnInit();

            triggerChanges(comp, { property: 'problemStatement', currentValue: problemStatement, previousValue: 'dolet amat', firstChange: false });
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

    describe('Analysis service integration test', () => {
        let exerciseHintService: IExerciseHintService;
        let getHintsForExerciseStub: SinonStub;

        const missingTestCases = ['test6', 'test7'];
        const invalidTestCases = ['test3', 'test4'];

        beforeEach(async () => {
            return TestBed.configureTestingModule({
                imports: [TranslateModule.forRoot(), ArtemisTestModule, NgbModule, ArtemisProgrammingExerciseInstructionsEditorModule],
            })
                .compileComponents()
                .then(() => {
                    fixture = TestBed.createComponent(ProgrammingExerciseInstructionAnalysisComponent);
                    comp = fixture.componentInstance;
                    debugElement = fixture.debugElement;

                    exerciseHintService = debugElement.injector.get(ExerciseHintService);
                    getHintsForExerciseStub = stub(exerciseHintService, 'findByExerciseId').returns(of({ body: exerciseHints }) as Observable<HttpResponse<ExerciseHint[]>>);
                });
        });

        afterEach(() => {
            getHintsForExerciseStub.restore();
        });

        it('should not render if no test cases were provided', () => {
            comp.problemStatement = problemStatement;
            comp.taskRegex = taskRegex;
            triggerChanges(comp, { property: 'problemStatement', currentValue: problemStatement });
            fixture.detectChanges();

            expect(debugElement.nativeElement.innerHtml).to.be.undefined;
            expect(comp.missingTestCases).to.be.empty;
            expect(comp.invalidTestCases).to.be.empty;
        });

        it('should render warnings on missing and invalid test cases', fakeAsync(() => {
            comp.problemStatement = problemStatement;
            comp.taskRegex = taskRegex;
            comp.exerciseTestCases = exerciseTestCases;
            comp.exerciseHints = exerciseHints;
            comp.ngOnInit();

            triggerChanges(
                comp,
                { property: 'problemStatement', currentValue: problemStatement, firstChange: false },
                { property: 'exerciseTestCases', currentValue: exerciseTestCases },
            );

            fixture.detectChanges();
            tick(500);

            expect(debugElement.nativeElement.innerHtml).not.to.equal('');
            expect(debugElement.query(By.css('fa-icon'))).to.exist;
            expect(comp.missingTestCases).to.be.deep.equal(missingTestCases);
            expect(comp.invalidTestCases).to.be.deep.equal(invalidTestCases);
        }));
    });
});
