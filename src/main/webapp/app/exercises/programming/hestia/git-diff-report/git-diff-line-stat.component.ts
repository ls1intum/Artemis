import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type LineStat = { addedLineCount: number; removedLineCount: number };

@Component({
    selector: 'jhi-git-diff-line-stat',
    templateUrl: './git-diff-line-stat.component.html',
    styleUrls: ['./git-diff-line-stat.component.scss'],
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GitDiffLineStatComponent {
    readonly lineStat = input<LineStat>();
    readonly addedLineCount = computed(() => this.lineStat()?.addedLineCount ?? 0);
    readonly removedLineCount = computed(() => this.lineStat()?.removedLineCount ?? 0);
    readonly squareCounts = computed(() => this.getSquareCounts());
    readonly addedSquareArray = computed(() => Array.from({ length: this.squareCounts().addedSquareCount }));
    readonly removedSquareArray = computed(() => Array.from({ length: this.squareCounts().removedSquareCount }));

    private getSquareCounts(): { addedSquareCount: number; removedSquareCount: number } {
        const addedLineCount = this.addedLineCount();
        const removedLineCount = this.removedLineCount();
        if (!addedLineCount && !removedLineCount) {
            return { addedSquareCount: 1, removedSquareCount: 1 };
        } else if (addedLineCount === 0) {
            return { addedSquareCount: 0, removedSquareCount: 5 };
        } else if (removedLineCount === 0) {
            return { addedSquareCount: 5, removedSquareCount: 0 };
        } else {
            const totalLineCount = addedLineCount + removedLineCount;
            // Calculates the amount of green rectangles to show between 1 and 4
            // This is the rounded percentage of added lines divided by total lines
            const addedSquareCount = Math.round(Math.max(1, Math.min(4, (addedLineCount / totalLineCount) * 5)));
            return { addedSquareCount, removedSquareCount: 5 - addedSquareCount };
        }
    }
}
