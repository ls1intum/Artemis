import { Component, Input, OnInit } from '@angular/core';
import { CoverageReport } from 'app/entities/hestia/coverage-report.model';
import { CoverageFileReport } from 'app/entities/hestia/coverage-file-report.model';

@Component({
    selector: 'jhi-testwise-coverage-report',
    templateUrl: './testwise-coverage-report.component.html',
})
export class TestwiseCoverageReportComponent implements OnInit {
    @Input()
    report: CoverageReport;

    @Input()
    fileContentByPath: Map<string, string>;

    displayedTestCaseNames: Map<string, boolean>;

    fileReportByFileName: Map<string, CoverageFileReport>;

    constructor() {}

    ngOnInit(): void {
        // initially display covered lines for all test cases
        this.displayedTestCaseNames = new Map<string, boolean>();
        // retrieve all test cases
        const testCases = new Set(
            this.report?.fileReports
                ?.flatMap((report) => report)
                .flatMap((fileReport) => fileReport.testwiseCoverageEntries)
                .map((entry) => entry!.testCase),
        );
        testCases.forEach((testCase) => this.displayedTestCaseNames.set(testCase!.testName!, true));

        this.setReportsByFileName();
    }

    changeReportsBySelectedTestCases(testCaseName: string): void {
        const selected = this.displayedTestCaseNames.get(testCaseName);
        this.displayedTestCaseNames.set(testCaseName, !selected);
        this.setReportsByFileName();
    }

    private setReportsByFileName(): void {
        const result = new Map<string, CoverageFileReport>();
        // create the reports for all files, not only for files that have existing coverage data
        this.fileContentByPath?.forEach((content, filePath) => {
            // do not include non-java/kotlin files
            if (!(filePath.endsWith('.java') || filePath.endsWith('.kt'))) {
                return;
            }
            const matchingFileReport = this.report.fileReports?.filter((fileReport) => fileReport?.filePath === filePath)?.first();
            const copiedFileReport = new CoverageFileReport();
            copiedFileReport.filePath = filePath;
            if (matchingFileReport) {
                copiedFileReport.lineCount = matchingFileReport.lineCount;
                copiedFileReport.coveredLineCount = matchingFileReport.coveredLineCount;

                // filter out entries for the current file report
                copiedFileReport.testwiseCoverageEntries = matchingFileReport.testwiseCoverageEntries?.filter((entry) =>
                    this.displayedTestCaseNames.get(entry.testCase!.testName!),
                );
            } else {
                copiedFileReport.lineCount = content.split('\n').length;
                copiedFileReport.coveredLineCount = 0;
                copiedFileReport.testwiseCoverageEntries = [];
            }
            result.set(filePath, copiedFileReport);
        });
        this.fileReportByFileName = result;
    }

    identifyCoverageFileComponent(index: Number, item: any) {
        return item.key;
    }
}
