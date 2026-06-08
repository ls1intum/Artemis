import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { DebugElement } from '@angular/core';
import { ProgrammingExerciseInstructionAnalysisComponent } from 'app/programming/manage/instructions-editor/analysis/programming-exercise-instruction-analysis.component';
import { ProgrammingExerciseInstructionAnalysisService } from 'app/programming/manage/instructions-editor/analysis/programming-exercise-instruction-analysis.service';
import { MockProgrammingExerciseInstructionAnalysisService } from 'test/helpers/mocks/service/mock-programming-exericse-instruction-analysis.service';
import { TaskAction } from 'app/editor/monaco-editor/model/actions/task.action';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('ProgrammingExerciseInstructionInstructorAnalysis', () => {
    setupTestBed({ zoneless: true });

    let comp: ProgrammingExerciseInstructionAnalysisComponent;
    let fixture: ComponentFixture<ProgrammingExerciseInstructionAnalysisComponent>;
    let debugElement: DebugElement;

    const testCaseOkId = 'instruction_analysis_test-case-ok';
    const testCaseIssuesId = 'instruction_analysis_test-case-issues';

    const taskRegex = TaskAction.GLOBAL_TASK_REGEX;
    const exerciseTestCases = ['test1', 'test2', 'test6', 'test7'];
    const problemStatement =
        '1. [task][SortStrategy Interface](test1,test2) \n 2. [task][SortStrategy Interface](test3) \n lorem ipsum \n lorem \n  3. [task][SortStrategy Interface](test2,test4)';

    describe('Component tests', () => {
        let analysisService: ProgrammingExerciseInstructionAnalysisService;

        let analyzeProblemStatementStub: ReturnType<typeof vi.spyOn>;
        let emitAnalysisSpy: ReturnType<typeof vi.spyOn>;

        beforeEach(() => {
            TestBed.configureTestingModule({
                providers: [
                    {
                        provide: ProgrammingExerciseInstructionAnalysisService,
                        useClass: MockProgrammingExerciseInstructionAnalysisService,
                    },
                    { provide: TranslateService, useClass: MockTranslateService },
                ],
            });
            fixture = TestBed.createComponent(ProgrammingExerciseInstructionAnalysisComponent);
            comp = fixture.componentInstance;
            debugElement = fixture.debugElement;

            analysisService = TestBed.inject(ProgrammingExerciseInstructionAnalysisService);

            analyzeProblemStatementStub = vi.spyOn(analysisService, 'analyzeProblemStatement');
            emitAnalysisSpy = vi.spyOn(comp.problemStatementAnalysis, 'emit');
        });

        afterEach(() => {
            vi.useRealTimers();
            vi.restoreAllMocks();
        });

        it('should display the received analysis from the service', () => {
            vi.useFakeTimers();

            const completeAnalysis = {
                '0': { invalidTestCases: ['artemisApp.programmingExercise.testCaseAnalysis.invalidTestCase'] },
                '2': {
                    invalidTestCases: ['artemisApp.programmingExercise.testCaseAnalysis.invalidTestCase'],
                },
            };
            const invalidTestCases = ['testMergeSort'];
            const missingTestCases = ['testBubbleSort'];
            const repeatedTestCases = ['testCaseQuickSort'];

            analyzeProblemStatementStub.mockReturnValue({
                completeAnalysis,
                invalidTestCases,
                missingTestCases,
                repeatedTestCases,
            });

            // dummy data.
            const testCases = ['testabc'];
            fixture.componentRef.setInput('problemStatement', problemStatement);
            fixture.componentRef.setInput('taskRegex', taskRegex);
            fixture.componentRef.setInput('exerciseTestCases', testCases);

            // The first detectChanges runs the component lifecycle (ngOnInit) which sets up the
            // debounced analysis subscription and triggers the initial analysis.
            fixture.detectChanges();
            vi.advanceTimersByTime(500);

            // check first analysis
            expect(comp.missingTestCases).toEqual(missingTestCases);
            expect(comp.invalidTestCases).toEqual(invalidTestCases);
            expect(comp.repeatedTestCases).toEqual(repeatedTestCases);

            // Use a different problem statement to trigger analysis again
            // (distinctUntilChanged skips identical problem statements)
            const updatedProblemStatement = problemStatement + ' updated';
            fixture.componentRef.setInput('problemStatement', updatedProblemStatement);
            fixture.detectChanges();
            vi.advanceTimersByTime(500); // Update is debounced, otherwise we would send updates on every change.
            fixture.detectChanges();

            // Check internal state of the component.
            expect(comp.missingTestCases).toEqual(missingTestCases);
            expect(comp.invalidTestCases).toEqual(invalidTestCases);
            expect(comp.repeatedTestCases).toEqual(repeatedTestCases);

            // Check that an event with the updated analysis is emitted.
            // We expect two calls, once in ngOnInit and once on the input change (with different problem statements)
            expect(emitAnalysisSpy).toHaveBeenCalledTimes(2);
            expect(emitAnalysisSpy).toHaveBeenCalledWith(completeAnalysis);

            // Check rendered html.
            const testCaseOk = debugElement.query(By.css(`#${testCaseOkId}`));
            const testCaseIssues = debugElement.query(By.css(`#${testCaseIssuesId}`));

            // Test cases are not ok according to the analysis.
            expect(testCaseOk).toBeNull();
            expect(testCaseIssues).not.toBeNull();
            vi.advanceTimersByTime(500);
        });

        describe('Analysis service integration test', () => {
            const missingTestCases = ['test6', 'test7'];
            const invalidTestCases = ['test3', 'test4'];
            const repeatedTestCases = ['test3', 'test4'];

            it('should not render if no test cases were provided', () => {
                fixture.componentRef.setInput('problemStatement', problemStatement);
                fixture.componentRef.setInput('taskRegex', taskRegex);
                fixture.detectChanges();

                expect(debugElement.nativeElement.innerHtml).toBeUndefined();
                expect(comp.missingTestCases).toEqual([]);
                expect(comp.invalidTestCases).toEqual([]);
                expect(comp.repeatedTestCases).toEqual([]);
            });

            it('should render warnings on missing, invalid and repeated test cases', () => {
                vi.useFakeTimers();

                const completeAnalysis = {
                    '0': {
                        invalidTestCases: ['artemisApp.programmingExercise.testCaseAnalysis.invalidTestCase'],
                        repeatedTestCases: ['artemisApp.programmingExercise.testCaseAnalysis.repeatedTestCase'],
                    },
                    '2': {
                        invalidTestCases: ['artemisApp.programmingExercise.testCaseAnalysis.invalidTestCase'],
                        repeatedTestCases: ['artemisApp.programmingExercise.testCaseAnalysis.repeatedTestCase'],
                    },
                };

                analyzeProblemStatementStub.mockReturnValue({ completeAnalysis, invalidTestCases, repeatedTestCases, missingTestCases });

                fixture.componentRef.setInput('problemStatement', problemStatement);
                fixture.componentRef.setInput('taskRegex', taskRegex);
                fixture.componentRef.setInput('exerciseTestCases', exerciseTestCases);

                fixture.detectChanges();
                vi.advanceTimersByTime(500);

                expect(debugElement.nativeElement.innerHtml).toBeUndefined();
                expect(debugElement.query(By.css('fa-icon'))).not.toBeNull();
                expect(comp.missingTestCases).toEqual(missingTestCases);
                expect(comp.invalidTestCases).toEqual(invalidTestCases);
                expect(comp.repeatedTestCases).toEqual(repeatedTestCases);
            });
        });
    });
});
