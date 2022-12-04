import { Component, Input, OnChanges } from '@angular/core';
import { IssuesMap } from 'app/entities/programming-exercise-test-case-statistics.model';
import { StaticCodeAnalysisCategory, StaticCodeAnalysisCategoryState } from 'app/entities/static-code-analysis-category.model';

export class IssueColumn {
    w: string;
    h: string;
    color: string;
    tooltip: string;
}

@Component({
    selector: 'jhi-category-issues-chart',
    template: `
        <div style="max-width: 120px; margin: auto;">
            <div style="height: 30px;" class="d-flex justify-content-between">
                <div
                    *ngFor="let column of columns"
                    class="d-flex align-items-end"
                    [style]="{ width: column.w, height: '30px' }"
                    [ngbTooltip]="column.tooltip"
                    placement="bottom auto"
                >
                    <div [style]="{ width: '100%', height: column.h, background: column.color }"></div>
                </div>
            </div>
        </div>
    `,
})
export class CategoryIssuesChartComponent implements OnChanges {
    @Input() issuesMap?: IssuesMap;
    @Input() category: StaticCodeAnalysisCategory;
    @Input() maxGradedIssues: number;
    @Input() totalStudents: number;
    @Input() maxNumberOfIssues: number;

    columns: IssueColumn[] = [];

    ngOnChanges(): void {
        // set a minimum of 10 columns
        const numColumns = Math.max(this.maxNumberOfIssues, 10) + 1;

        // display properties
        const columnGap = 2;
        const columnWidth = (100 + columnGap) / numColumns - columnGap;

        const maxGradedIssues = this.category.penalty > 0 ? this.category.maxPenalty / this.category.penalty : 0;

        const columns = new Array(numColumns).fill(0).map((column, i) => {
            const numIssues = i + 1;
            const numStudents = this.issuesMap ? this.issuesMap[numIssues] || 0 : 0;
            return {
                w: columnWidth + '%',
                h: (this.totalStudents > 0 ? (numStudents / this.totalStudents) * 95 : 0) + 4 + '%',
                color:
                    this.category.state === StaticCodeAnalysisCategoryState.Inactive
                        ? '#ddd'
                        : numStudents === 0 || this.category.state !== StaticCodeAnalysisCategoryState.Graded
                        ? '#28a745'
                        : numIssues > maxGradedIssues
                        ? '#dc3545'
                        : '#ffc107',
                tooltip: `${numStudents} student${numStudents !== 1 ? 's' : ''} ${numStudents !== 1 ? 'have' : 'has'} ${numIssues} issue${numIssues !== 1 ? 's' : ''}.`,
            };
        });

        setTimeout(() => (this.columns = columns));
    }
}
