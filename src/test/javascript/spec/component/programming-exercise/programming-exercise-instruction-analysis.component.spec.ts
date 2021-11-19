import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { DebugElement } from '@angular/core';
import { ExerciseHint } from 'app/entities/exercise-hint.model';
import { TaskCommand } from 'app/shared/markdown-editor/domainCommands/programming-exercise/task.command';
import { triggerChanges } from '../../helpers/utils/general.utils';
import { ProgrammingExerciseInstructionAnalysisComponent } from 'app/exercises/programming/manage/instructions-editor/analysis/programming-exercise-instruction-analysis.component';
import { ProgrammingExerciseInstructionAnalysisService } from 'app/exercises/programming/manage/instructions-editor/analysis/programming-exercise-instruction-analysis.service';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockProgrammingExerciseInstructionAnalysisService } from '../../helpers/mocks/service/mock-programming-exericse-instruction-analysis.service';

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

        let analyzeProblemStatementStub: jest.SpyInstance;
        let emitAnalysisSpy: jest.SpyInstance;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [],
                declarations: [ProgrammingExerciseInstructionAnalysisComponent, MockDirective(NgbTooltip), MockPipe(ArtemisTranslatePipe), MockComponent(FaIconComponent)],
                providers: [{ provide: ProgrammingExerciseInstructionAnalysisService, useClass: MockProgrammingExerciseInstructionAnalysisService }],
            })
                .compileComponents()
                .then(() => {
                    fixture = TestBed.createComponent(ProgrammingExerciseInstructionAnalysisComponent);
                    comp = fixture.componentInstance;
                    debugElement = fixture.debugElement;

                    analysisService = debugElement.injector.get(ProgrammingExerciseInstructionAnalysisService);

                    analyzeProblemStatementStub = jest.spyOn(analysisService, 'analyzeProblemStatement');
                    emitAnalysisSpy = jest.spyOn(comp.problemStatementAnalysis, 'emit');
                });
        });

        afterEach(() => {
            jest.restoreAllMocks();
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

            analyzeProblemStatementStub.mockReturnValue({ completeAnalysis, invalidTestCases, invalidHints, missingTestCases });

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

            // Check internal state of the component.
            expect(comp.missingTestCases).toEqual(missingTestCases);
            expect(comp.invalidTestCases).toEqual(invalidTestCases);
            expect(comp.invalidHints).toEqual(invalidHints);

            // Check that an event with the updated analysis is emitted.
            expect(emitAnalysisSpy).toHaveBeenCalledTimes(1);
            expect(emitAnalysisSpy).toHaveBeenCalledWith(completeAnalysis);

            // Check rendered html.
            const testCaseOk = debugElement.query(By.css(`#${testCaseOkId}`));
            const testCaseIssues = debugElement.query(By.css(`#${testCaseIssuesId}`));
            const hintOk = debugElement.query(By.css(`#${hintOkId}`));
            const hintIssues = debugElement.query(By.css(`#${hintIssuesId}`));

            // Test cases are not ok according to the analysis.
            expect(testCaseOk).toBe(null);
            expect(testCaseIssues).not.toBe(null);

            // Hints are ok according to the analysis.
            expect(hintOk).not.toBe(null);
            expect(hintIssues).toBe(null);
        }));

        describe('Analysis service integration test', () => {
            const missingTestCases = ['test6', 'test7'];
            const invalidTestCases = ['test3', 'test4'];

            it('should not render if no test cases were provided', () => {
                comp.problemStatement = problemStatement;
                comp.taskRegex = taskRegex;
                triggerChanges(comp, { property: 'problemStatement', currentValue: problemStatement });
                fixture.detectChanges();

                expect(debugElement.nativeElement.innerHtml).toBe(undefined);
                expect(comp.missingTestCases).toEqual([]);
                expect(comp.invalidTestCases).toEqual([]);
            });

            it('should render warnings on missing and invalid test cases', fakeAsync(() => {
                comp.problemStatement = problemStatement;
                comp.taskRegex = taskRegex;
                comp.exerciseTestCases = exerciseTestCases;
                comp.exerciseHints = exerciseHints;

                const completeAnalysis = {
                    '0': { invalidTestCases: ['artemisApp.programmingExercise.testCaseAnalysis.invalidTestCase'] },
                    '2': {
                        invalidTestCases: ['artemisApp.programmingExercise.testCaseAnalysis.invalidTestCase'],
                    },
                };

                analyzeProblemStatementStub.mockReturnValue({ completeAnalysis, invalidTestCases, missingTestCases });

                comp.ngOnInit();

                triggerChanges(
                    comp,
                    { property: 'problemStatement', currentValue: problemStatement, firstChange: false },
                    { property: 'exerciseTestCases', currentValue: exerciseTestCases },
                );

                fixture.detectChanges();
                tick(500);

                expect(debugElement.nativeElement.innerHtml).toBe(undefined);
                expect(debugElement.query(By.css('fa-icon'))).not.toBe(null);
                expect(comp.missingTestCases).toEqual(missingTestCases);
                expect(comp.invalidTestCases).toEqual(invalidTestCases);
            }));
        });
    });
});
