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

    function checkBoxesCount() {
        comp.addedLineCount = 100;
        comp.removedLineCount = 0;
        comp.ngOnInit();
        expect(comp.addedSquareCount).toBe(5);
        expect(comp.removedSquareCount).toBe(0);
    }

    it('Should show 5-0 boxes', () => {
        checkBoxesCount();
    });

    it('Should show 4-1 boxes', () => {
        comp.addedLineCount = 8;
        comp.removedLineCount = 2;
        comp.ngOnInit();
        expect(comp.addedSquareCount).toBe(4);
        expect(comp.removedSquareCount).toBe(1);
    });

    it('Should show 3-2 boxes', () => {
        comp.addedLineCount = 6;
        comp.removedLineCount = 4;
        comp.ngOnInit();
        expect(comp.addedSquareCount).toBe(3);
        expect(comp.removedSquareCount).toBe(2);
    });

    it('Should show 2-3 boxes', () => {
        comp.addedLineCount = 4;
        comp.removedLineCount = 6;
        comp.ngOnInit();
        expect(comp.addedSquareCount).toBe(2);
        expect(comp.removedSquareCount).toBe(3);
    });

    it('Should show 1-4 boxes', () => {
        comp.addedLineCount = 2;
        comp.removedLineCount = 8;
        comp.ngOnInit();
        expect(comp.addedSquareCount).toBe(1);
        expect(comp.removedSquareCount).toBe(4);
    });

    it('Should show 0-5 boxes', () => {
        comp.addedLineCount = 0;
        comp.removedLineCount = 100;
        comp.ngOnInit();
        expect(comp.addedSquareCount).toBe(0);
        expect(comp.removedSquareCount).toBe(5);
    });
});
