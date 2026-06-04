import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GitDiffLineStatComponent } from 'app/programming/shared/git-diff-report/git-diff-line-stat/git-diff-line-stat.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';

describe('GitDiffLineStatComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: GitDiffLineStatComponent;
    let fixture: ComponentFixture<GitDiffLineStatComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [GitDiffLineStatComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [],
        }).compileComponents();
        fixture = TestBed.createComponent(GitDiffLineStatComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    const boxesTestTable = [
        [5, 0, 100, 0],
        [4, 1, 8, 2],
        [3, 2, 6, 4],
        [2, 3, 4, 6],
        [1, 4, 2, 8],
        [0, 5, 0, 100],
        [1, 1, 0, 0],
    ];

    it.each(boxesTestTable)('Should show %s-%s boxes', (expectedAddedSquareCount, expectedRemovedSquareCount, addedLineCount, removedLineCount) => {
        fixture.componentRef.setInput('addedLineCount', addedLineCount);
        fixture.componentRef.setInput('removedLineCount', removedLineCount);
        fixture.detectChanges();
        expect(comp.squareCounts().addedSquareCount).toBe(expectedAddedSquareCount);
        expect(comp.squareCounts().removedSquareCount).toBe(expectedRemovedSquareCount);
    });
});
