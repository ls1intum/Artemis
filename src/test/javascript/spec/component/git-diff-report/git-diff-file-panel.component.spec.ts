import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective } from 'ng-mocks';
import { GitDiffLineStatComponent } from '../../../../../main/webapp/app/exercises/programming/git-diff-report/git-diff-line-stat.component';
import { ProgrammingExerciseGitDiffEntry } from '../../../../../main/webapp/app/entities/programming-exercise-git-diff-entry.model';
import { GitDiffFilePanelComponent } from '../../../../../main/webapp/app/exercises/programming/git-diff-report/git-diff-file-panel.component';
import { NgbAccordionButton, NgbAccordionCollapse, NgbAccordionDirective, NgbAccordionHeader, NgbAccordionItem } from '@ng-bootstrap/ng-bootstrap';
import { GitDiffFileComponent } from '../../../../../main/webapp/app/exercises/programming/git-diff-report/git-diff-file.component';
import { GitDiffFilePanelTitleComponent } from '../../../../../main/webapp/app/exercises/programming/git-diff-report/git-diff-file-panel-title.component';
import { MonacoDiffEditorComponent } from '../../../../../main/webapp/app/shared/monaco-editor/monaco-diff-editor.component';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('GitDiffFilePanelComponent', () => {
    let comp: GitDiffFilePanelComponent;
    let fixture: ComponentFixture<GitDiffFilePanelComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            // TODO: We cannot mock GitDiffFileComponent because of https://github.com/help-me-mom/ng-mocks/issues/8634.
            imports: [GitDiffFileComponent],
            declarations: [
                GitDiffFilePanelComponent,
                MockComponent(GitDiffFilePanelTitleComponent),
                MockComponent(GitDiffLineStatComponent),
                MockComponent(MonacoDiffEditorComponent),
                MockDirective(NgbAccordionDirective),
                MockDirective(NgbAccordionItem),
                MockDirective(NgbAccordionHeader),
                MockDirective(NgbAccordionButton),
                MockDirective(NgbAccordionCollapse),
            ],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
        fixture = TestBed.createComponent(GitDiffFilePanelComponent);
        comp = fixture.componentInstance;
        fixture.componentRef.setInput('originalFileContent', 'L1\nL2\nL3\nL4\nL5\nL6\nL7\nL8\nL9\nL10\nL11\nL12\nL13\nL14\nL15\nL16');
        fixture.componentRef.setInput('modifiedFileContent', 'L1\nL2\nL3\nL4\nL5\nL6\nL7\nL8\nL9\nL10\nL11\nL12\nL13\nL14\nL15\nL16');
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('Should extract file path', () => {
        fixture.componentRef.setInput('diffEntries', [{ filePath: 'src/a.java', previousFilePath: 'src/b.java' }] as ProgrammingExerciseGitDiffEntry[]);
        fixture.detectChanges();
        expect(comp.modifiedFilePath()).toBe('src/a.java');
        expect(comp.originalFilePath()).toBe('src/b.java');
    });

    it('Should set added/removed lines to 1-0', () => {
        fixture.componentRef.setInput('diffEntries', [{ filePath: 'src/a.java', startLine: 1, lineCount: 1 }] as ProgrammingExerciseGitDiffEntry[]);
        fixture.detectChanges();
        expect(comp.addedLineCount()).toBe(1);
        expect(comp.removedLineCount()).toBe(0);
    });

    it('Should set added/removed lines to 4-1', () => {
        fixture.componentRef.setInput('diffEntries', [
            {
                filePath: 'src/a.java',
                previousFilePath: 'src/a.java',
                startLine: 5,
                lineCount: 4,
                previousStartLine: 5,
                previousLineCount: 1,
            },
        ] as ProgrammingExerciseGitDiffEntry[]);
        fixture.detectChanges();
        expect(comp.addedLineCount()).toBe(4);
        expect(comp.removedLineCount()).toBe(1);
    });

    it('Should set added/removed lines to 3-2', () => {
        fixture.componentRef.setInput('diffEntries', [
            {
                filePath: 'src/a.java',
                previousFilePath: 'src/a.java',
                startLine: 5,
                lineCount: 3,
                previousStartLine: 5,
                previousLineCount: 2,
            },
        ] as ProgrammingExerciseGitDiffEntry[]);
        fixture.detectChanges();
        expect(comp.addedLineCount()).toBe(3);
        expect(comp.removedLineCount()).toBe(2);
    });

    it('Should set added/removed lines to 2-3', () => {
        fixture.componentRef.setInput('diffEntries', [
            {
                filePath: 'src/a.java',
                previousFilePath: 'src/a.java',
                startLine: 5,
                lineCount: 2,
                previousStartLine: 5,
                previousLineCount: 3,
            },
        ] as ProgrammingExerciseGitDiffEntry[]);
        fixture.detectChanges();
        expect(comp.addedLineCount()).toBe(2);
        expect(comp.removedLineCount()).toBe(3);
    });

    it('Should set added/removed lines to 1-4', () => {
        fixture.componentRef.setInput('diffEntries', [
            {
                filePath: 'src/a.java',
                previousFilePath: 'src/a.java',
                startLine: 5,
                lineCount: 1,
                previousStartLine: 5,
                previousLineCount: 4,
            },
        ] as ProgrammingExerciseGitDiffEntry[]);
        fixture.detectChanges();
        expect(comp.addedLineCount()).toBe(1);
        expect(comp.removedLineCount()).toBe(4);
    });

    it('Should set added/removed lines to 0-1', () => {
        fixture.componentRef.setInput('diffEntries', [
            {
                filePath: 'src/a.java',
                previousFilePath: 'src/a.java',
                previousStartLine: 1,
                previousLineCount: 1,
            },
        ] as ProgrammingExerciseGitDiffEntry[]);
        fixture.detectChanges();
        expect(comp.addedLineCount()).toBe(0);
        expect(comp.removedLineCount()).toBe(1);
    });
});
