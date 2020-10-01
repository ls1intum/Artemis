import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';

export class IssueColumn {
    w: string;
    h: string;
    color: string;
    tooltip: string;
}

@Component({
    selector: 'jhi-category-issues-graph',
    template: `
        <div style="max-width: 120px; margin: auto;">
            <div style="height: 30px;" class="d-flex justify-content-between">
                <div *ngFor="let column of columns" class="d-flex align-items-end" [style]="{ width: column.w, height: '30px' }" [ngbTooltip]="column.tooltip" placement="bottom">
                    <div [style]="{ width: '100%', height: column.h, background: column.color }"></div>
                </div>
            </div>
        </div>
    `,
})
export class CategoryIssuesGraphComponent implements OnChanges {
    @Input() categoryIssuesStats?: { [numIssues: string]: number };
    @Input() maxGradedIssues: number;
    @Input() totalStudents: number;
    @Input() maxNumberOfIssues: number;

    columns: IssueColumn[] = [];

    ngOnChanges(): void {
        if (!this.totalStudents) {
            return;
        }

        const numColumns = Math.max(this.maxNumberOfIssues, 10) + 1;
        const columnGap = 2;
        const columnWidth = (100 + columnGap) / numColumns - columnGap;

        const columns = new Array(numColumns).fill(0).map((column, i, { length }) => {
            const numIssues = length - i - 1;
            const numStudents = this.categoryIssuesStats ? this.categoryIssuesStats[numIssues] || 0 : 0;
            return {
                w: columnWidth + '%',
                h: (numStudents / this.totalStudents) * 95 + 5 + '%',
                color: numIssues > this.maxGradedIssues ? '#dc3545' : '#ffc107',
                tooltip: `${numStudents} student${numStudents !== 1 ? 's' : ''} have ${numIssues} issue${numIssues !== 1 ? 's' : ''}.`,
            };
        });

        setTimeout(() => (this.columns = columns));
    }
}
