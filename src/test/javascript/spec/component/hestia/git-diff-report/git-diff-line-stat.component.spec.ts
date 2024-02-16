import { ArtemisTestModule } from '../../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GitDiffLineStatComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-line-stat.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';

describe('Git-Diff line-stat Component', () => {
    let comp: GitDiffLineStatComponent;
    let fixture: ComponentFixture<GitDiffLineStatComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [GitDiffLineStatComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [],
        }).compileComponents();
        fixture = TestBed.createComponent(GitDiffLineStatComponent);
        comp = fixture.componentInstance;
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
        comp.addedLineCount = addedLineCount;
        comp.removedLineCount = removedLineCount;
        comp.ngOnInit();
        expect(comp.addedSquareCount).toBe(expectedAddedSquareCount);
        expect(comp.removedSquareCount).toBe(expectedRemovedSquareCount);
    });
});
