import { Component, Input, OnInit } from '@angular/core';
import { ProgrammingExerciseTestwiseCoverageReport } from 'app/entities/hestia/programming-exercise-testwise-coverage-report.model';

@Component({
    selector: 'jhi-testwise-coverage-report',
    templateUrl: './testwise-coverage-report.component.html',
})
export class TestwiseCoverageReportComponent implements OnInit {
    @Input()
    reports: ProgrammingExerciseTestwiseCoverageReport[];

    @Input()
    fileContentByPath: Map<string, string>;

    displayedTestCaseNames: Map<string, boolean>;

    reportsByFileName: Map<string, ProgrammingExerciseTestwiseCoverageReport[]>;

    constructor() {}

    ngOnInit(): void {
        // initially display covered lines for all test cases
        this.displayedTestCaseNames = new Map<string, boolean>();
        this.reports.forEach((report) => this.displayedTestCaseNames.set(report.testCase!.testName!, true));

        this.setReportsByFileName();
    }

    public changeReportsBySelectedTestCases(testCaseName: string): void {
        const selected = this.displayedTestCaseNames.get(testCaseName) ?? false;
        this.displayedTestCaseNames.set(testCaseName, !selected);
        this.setReportsByFileName();
    }

    private setReportsByFileName(): void {
        const result = new Map<string, ProgrammingExerciseTestwiseCoverageReport[]>();
        // get reports that only contains entries about a file path for all files
        this.fileContentByPath.forEach((fileContent, filePath) => {
            // filter out reports for the current filePath
            const reports = this.reports
                .filter((report) => !!this.displayedTestCaseNames.get(report.testCase!.testName!))
                .map((report) => {
                    const copiedReport = new ProgrammingExerciseTestwiseCoverageReport();
                    copiedReport.id = report.id;
                    copiedReport.testCase = report.testCase;
                    copiedReport.entries = report.entries?.filter((entry) => 'src/' + entry.filePath === filePath);
                    return copiedReport;
                });
            result.set(filePath, reports);
        });
        this.reportsByFileName = result;
    }
}
