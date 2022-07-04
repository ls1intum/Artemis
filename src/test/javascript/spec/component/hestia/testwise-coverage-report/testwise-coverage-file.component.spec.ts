import { ArtemisTestModule } from '../../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { TestwiseCoverageFileComponent } from 'app/exercises/programming/hestia/testwise-coverage-report/testwise-coverage-file.component';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise-test-case.model';
import { TestwiseCoverageReportEntry } from 'app/entities/hestia/testwise-coverage-report-entry.model';
import { CoverageFileReport } from 'app/entities/hestia/coverage-file-report.model';
import { MatExpansionModule } from '@angular/material/expansion';
import ace from 'brace';
import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';

describe('TestwiseCoverageFile Component', () => {
    let comp: TestwiseCoverageFileComponent;
    let fixture: ComponentFixture<TestwiseCoverageFileComponent>;
    let reportEntry1: TestwiseCoverageReportEntry;
    let reportEntry2: TestwiseCoverageReportEntry;
    let fileReport: CoverageFileReport;
    let range1: ace.Range;
    let range2: ace.Range;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MatExpansionModule, AceEditorModule, NoopAnimationsModule],
            declarations: [TestwiseCoverageFileComponent],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TestwiseCoverageFileComponent);
                comp = fixture.componentInstance;

                ace.Range = ace.acequire('ace/range').Range;
                range1 = new ace.Range(3, 0, 4, 1);
                range2 = new ace.Range(6, 0, 7, 1);

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
        const addMarkerSpy = jest.spyOn(comp.editor.getEditor().getSession(), 'addMarker');

        comp.ngOnInit();
        // the row values are equal to (line number - 1) because the row counting starts for the editor at 0
        expect(addMarkerSpy).toHaveBeenCalledTimes(2);
        expect(addMarkerSpy).toHaveBeenCalledWith(range1, 'ace_highlight-marker', 'fullLine');
        expect(addMarkerSpy).toHaveBeenCalledWith(range2, 'ace_highlight-marker', 'fullLine');
    });

    it('should calculate covered line ratio correctly', () => {
        comp.fileReport.testwiseCoverageEntries = [reportEntry1];
        comp.ngOnInit();
        expect(comp.proportionCoveredLines).toBe(0.2);
    });

    it('should update on input coverage data changes', () => {
        comp.fileReport.testwiseCoverageEntries = [reportEntry1, reportEntry2];
        comp.ngOnInit();
        expect(comp.proportionCoveredLines).toBe(0.4);
        const addMarkerSpy = jest.spyOn(comp.editor.getEditor().getSession(), 'addMarker');

        fileReport.testwiseCoverageEntries = [reportEntry1];
        comp.fileReport = fileReport;
        fixture.detectChanges();

        expect(comp.proportionCoveredLines).toBe(0.2);
        expect(addMarkerSpy).toHaveBeenCalledOnce();
        expect(addMarkerSpy).toHaveBeenCalledWith(range1, 'ace_highlight-marker', 'fullLine');
    });
});
