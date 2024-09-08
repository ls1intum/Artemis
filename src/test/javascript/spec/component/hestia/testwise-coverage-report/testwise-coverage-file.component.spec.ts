import { ArtemisTestModule } from '../../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { TestwiseCoverageFileComponent } from 'app/exercises/programming/hestia/testwise-coverage-report/testwise-coverage-file.component';
import { ProgrammingExerciseTestCase } from 'app/entities/programming/programming-exercise-test-case.model';
import { TestwiseCoverageReportEntry } from 'app/entities/hestia/testwise-coverage-report-entry.model';
import { CoverageFileReport } from 'app/entities/hestia/coverage-file-report.model';
import { MatExpansionModule } from '@angular/material/expansion';
import { MockComponent } from 'ng-mocks';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';

describe('TestwiseCoverageFile Component', () => {
    let comp: TestwiseCoverageFileComponent;
    let fixture: ComponentFixture<TestwiseCoverageFileComponent>;
    let reportEntry1: TestwiseCoverageReportEntry;
    let reportEntry2: TestwiseCoverageReportEntry;
    let fileReport: CoverageFileReport;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MatExpansionModule, NoopAnimationsModule],
            declarations: [TestwiseCoverageFileComponent, MockComponent(MonacoEditorComponent)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TestwiseCoverageFileComponent);
                comp = fixture.componentInstance;

                const testCase1 = {
                    id: 1,
                    testName: 'testBubbleSort()',
                } as ProgrammingExerciseTestCase;
                const testCase2 = {
                    id: 2,
                    testName: 'testBubbleSortForSmallLists()',
                } as ProgrammingExerciseTestCase;

                reportEntry1 = {
                    id: 1,
                    startLine: 4,
                    lineCount: 2,
                    testCase: testCase1,
                } as TestwiseCoverageReportEntry;
                reportEntry2 = {
                    id: 2,
                    startLine: 7,
                    lineCount: 2,
                    testCase: testCase2,
                } as TestwiseCoverageReportEntry;

                fileReport = {
                    id: 1,
                    lineCount: 10,
                    filePath: 'src/de/tum/in/ase/BubbleSort.java',
                    coveredLineCount: 5,
                    testwiseCoverageEntries: [reportEntry1, reportEntry2],
                } as CoverageFileReport;
                comp.fileContent = '\n\n\n\n\n\n\n\n\nlast';
                comp.fileName = 'src/de/tum/in/ase/BubbleSort.java';
                comp.fileReport = fileReport;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initially create and add all ranges correctly', () => {
        const highlightLinesSpy = jest.spyOn(comp.editor, 'highlightLines');

        comp.ngOnInit();

        expect(highlightLinesSpy).toHaveBeenCalledTimes(2);
        expect(highlightLinesSpy).toHaveBeenCalledWith(
            4,
            5,
            TestwiseCoverageFileComponent.COVERED_LINE_HIGHLIGHT_CLASS,
            TestwiseCoverageFileComponent.COVERED_LINE_HIGHLIGHT_CLASS,
        );
        expect(highlightLinesSpy).toHaveBeenCalledWith(
            7,
            8,
            TestwiseCoverageFileComponent.COVERED_LINE_HIGHLIGHT_CLASS,
            TestwiseCoverageFileComponent.COVERED_LINE_HIGHLIGHT_CLASS,
        );
    });

    it('should calculate covered line ratio correctly', () => {
        comp.fileReport.testwiseCoverageEntries = [reportEntry1];
        comp.ngOnInit();
        expect(comp.proportionCoveredLines).toBe(0.2);
        expect(comp.proportionString).toBe('20.0 %');
    });

    it('should update on input coverage data changes', () => {
        comp.fileReport.testwiseCoverageEntries = [reportEntry1, reportEntry2];
        comp.ngOnInit();
        expect(comp.proportionCoveredLines).toBe(0.4);
        expect(comp.proportionString).toBe('40.0 %');

        const highlightLinesSpy = jest.spyOn(comp.editor, 'highlightLines');

        fileReport.testwiseCoverageEntries = [reportEntry1];
        comp.fileReport = fileReport;
        fixture.detectChanges();

        expect(comp.proportionCoveredLines).toBe(0.2);
        expect(comp.proportionString).toBe('20.0 %');
        expect(highlightLinesSpy).toHaveBeenCalledExactlyOnceWith(
            4,
            5,
            TestwiseCoverageFileComponent.COVERED_LINE_HIGHLIGHT_CLASS,
            TestwiseCoverageFileComponent.COVERED_LINE_HIGHLIGHT_CLASS,
        );
    });
});
