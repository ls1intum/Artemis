import { ArtemisTestModule } from '../../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GitDiffFilePanelTitleComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-file-panel-title.component';

describe('GitDiffFilePanelTitleComponent', () => {
    let comp: GitDiffFilePanelTitleComponent;
    let fixture: ComponentFixture<GitDiffFilePanelTitleComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [],
            providers: [],
        }).compileComponents();
        fixture = TestBed.createComponent(GitDiffFilePanelTitleComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it.each([
        {
            filePath: 'some-unchanged-file.java',
            previousFilePath: 'some-unchanged-file.java',
            status: 'unchanged',
            title: 'some-unchanged-file.java',
        },
        {
            filePath: undefined,
            previousFilePath: 'some-deleted-file.java',
            status: 'deleted',
            title: 'some-deleted-file.java',
        },
        {
            filePath: 'some-created-file.java',
            previousFilePath: undefined,
            status: 'created',
            title: 'some-created-file.java',
        },
        {
            filePath: 'some-renamed-file.java',
            previousFilePath: 'some-file.java',
            status: 'renamed',
            title: 'some-file.java → some-renamed-file.java',
        },
    ])('should correctly set title and status', ({ filePath, previousFilePath, status, title }) => {
        comp.previousFilePath = previousFilePath;
        comp.filePath = filePath;
        fixture.detectChanges();
        expect(comp.title).toBe(title);
        expect(comp.fileStatus).toBe(status);
    });
});
