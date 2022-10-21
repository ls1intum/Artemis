import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CoverageFileReport } from 'app/entities/hestia/coverage-file-report.model';
import { CoverageReport } from 'app/entities/hestia/coverage-report.model';
import { TestwiseCoverageReportEntry } from 'app/entities/hestia/testwise-coverage-report-entry.model';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise-test-case.model';
import { TestwiseCoverageReportComponent } from 'app/exercises/programming/hestia/testwise-coverage-report/testwise-coverage-report.component';
import { ArtemisTestModule } from '../../../test.module';

describe('TestwiseCoverageReport Component', () => {
    let comp: TestwiseCoverageReportComponent;
    let fixture: ComponentFixture<TestwiseCoverageReportComponent>;

    let report: CoverageReport;
    let reportEntries: TestwiseCoverageReportEntry[];
    let fileReports: CoverageFileReport[];
    let fileContentByName: Map<string, string>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [TestwiseCoverageReportComponent],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TestwiseCoverageReportComponent);
                comp = fixture.componentInstance;

                const testCase1 = {
                    id: 1,
                    testName: 'testBubbleSort()',
                } as ProgrammingExerciseTestCase;
                const testCase2 = {
                    id: 2,
                    testName: 'testBubbleSortForSmallLists()',
                } as ProgrammingExerciseTestCase;
                const testCase3 = {
                    id: 3,
                    testName: 'testMergeSort()',
                } as ProgrammingExerciseTestCase;

                const reportEntry1 = {
                    id: 1,
                    startLine: 4,
                    lineCount: 2,
                    testCase: testCase1,
                } as TestwiseCoverageReportEntry;
                const reportEntry2 = {
                    id: 2,
                    startLine: 7,
                    lineCount: 2,
                    testCase: testCase2,
                } as TestwiseCoverageReportEntry;
                const reportEntry3 = {
                    id: 3,
                    startLine: 1,
                    lineCount: 2,
                    testCase: testCase3,
                } as TestwiseCoverageReportEntry;
                reportEntries = [reportEntry1, reportEntry2, reportEntry3];

                fileReports = [
                    {
                        filePath: 'src/de/tum/in/ase/BubbleSort.java',
                        lineCount: 10,
                        coveredLineCount: 5,
                        testwiseCoverageEntries: [reportEntry1, reportEntry2],
                    } as CoverageFileReport,
                    {
                        filePath: 'src/de/tum/in/ase/MergeSort.java',
                        lineCount: 2,
                        coveredLineCount: 1,
                        testwiseCoverageEntries: [reportEntry3],
                    } as CoverageFileReport,
                ];

                report = {
                    id: 1,
                    fileReports,
                    coveredLineRatio: 0.5,
                } as CoverageReport;
                comp.report = report;

                fileContentByName = new Map();
                // file with 10 lines, 4 covered
                fileContentByName.set(
                    'src/de/tum/in/ase/BubbleSort.java',
                    'package de.tum.in.ase;\n\ncovered\ncovered\nuncovered\ncovered\ncovered\nuncovered\nuncovered\nuncovered',
                );
                // file with 2 lines, 2 covered
                fileContentByName.set('src/de/tum/in/ase/MergeSort.java', 'covered\ncovered');
                comp.fileContentByPath = fileContentByName;

                comp.ngOnInit();
            });
    });

    it('should initially display coverage for all test cases', () => {
        const expectedMap = new Map<string, boolean>();
        expectedMap.set('testBubbleSort()', true);
        expectedMap.set('testBubbleSortForSmallLists()', true);
        expectedMap.set('testMergeSort()', true);

        expect(comp.displayedTestCaseNames.size).toBe(3);
        expect(comp.displayedTestCaseNames).toEqual(expectedMap);
    });

    it('should set file report by file name with for all test cases', () => {
        const expectedMap = new Map<string, CoverageFileReport>();
        expectedMap.set('src/de/tum/in/ase/BubbleSort.java', fileReports[0]);
        expectedMap.set('src/de/tum/in/ase/MergeSort.java', fileReports[1]);

        expect(comp.fileReportByFileName).toEqual(expectedMap);
    });

    it('should filter coverage file reports for test case', () => {
        comp.changeReportsBySelectedTestCases('testBubbleSort()');
        expect(comp.displayedTestCaseNames.get('testBubbleSort()')).toBeFalse();
        expect(comp.fileReportByFileName.size).toBe(2);
        expect(comp.fileReportByFileName.get('src/de/tum/in/ase/BubbleSort.java')?.testwiseCoverageEntries).toEqual([reportEntries[1]]);
    });

    it('should create empty file report if no report exists for file', () => {
        comp.fileContentByPath.set('notexisting.java', '\n\n');
        comp.ngOnInit();

        expect(comp.fileReportByFileName.get('notexisting.java')).toEqual({
            lineCount: 3,
            coveredLineCount: 0,
            filePath: 'notexisting.java',
            testwiseCoverageEntries: [],
        } as CoverageFileReport);
    });
});
