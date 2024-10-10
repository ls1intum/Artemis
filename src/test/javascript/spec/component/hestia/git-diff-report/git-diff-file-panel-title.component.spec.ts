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
            modifiedFilePath: 'some-unchanged-file.java',
            originalFilePath: 'some-unchanged-file.java',
            status: 'unchanged',
            title: 'some-unchanged-file.java',
        },
        {
            modifiedFilePath: undefined,
            originalFilePath: 'some-deleted-file.java',
            status: 'deleted',
            title: 'some-deleted-file.java',
        },
        {
            modifiedFilePath: 'some-created-file.java',
            originalFilePath: undefined,
            status: 'created',
            title: 'some-created-file.java',
        },
        {
            modifiedFilePath: 'some-renamed-file.java',
            originalFilePath: 'some-file.java',
            status: 'renamed',
            title: 'some-file.java → some-renamed-file.java',
        },
    ])('should correctly set title and status', ({ originalFilePath, modifiedFilePath, status, title }) => {
        fixture.componentRef.setInput('originalFilePath', originalFilePath);
        fixture.componentRef.setInput('modifiedFilePath', modifiedFilePath);
        fixture.detectChanges();
        expect(comp.title()).toBe(title);
        expect(comp.fileStatus()).toBe(status);
    });
});
