import { ArtemisTestModule } from '../../../test.module';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { MockActivatedRoute } from '../../../helpers/mocks/activated-route/mock-activated-route';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { MockProfileService } from '../../../helpers/mocks/service/mock-profile.service';
import { GitDiffEntryComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-entry.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ActivatedRoute } from '@angular/router';
import { ProgrammingExerciseGitDiffEntry } from 'app/entities/hestia/programming-exercise-git-diff-entry.model';
import { AceEditorComponent } from 'app/shared/markdown-editor/ace-editor/ace-editor.component';

describe('ProgrammingExercise Management Detail Component', () => {
    let comp: GitDiffEntryComponent;
    let fixture: ComponentFixture<GitDiffEntryComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TranslateModule.forRoot()],
            declarations: [GitDiffEntryComponent, AceEditorComponent],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: ProfileService, useValue: new MockProfileService() },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(GitDiffEntryComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('Should setup editors', () => {
        jest.spyOn(comp.editorNow.getEditor(), 'setOptions');
        jest.spyOn(comp.editorNow.getEditor().getSession(), 'setValue');
        jest.spyOn(comp.editorPrevious.getEditor(), 'setOptions');
        jest.spyOn(comp.editorPrevious.getEditor().getSession(), 'setValue');

        comp.diffEntry = new ProgrammingExerciseGitDiffEntry();
        comp.diffEntry.id = 123;
        comp.diffEntry.filePath = '/src/de/test.java';
        comp.diffEntry.previousLine = 1;
        comp.diffEntry.line = 1;
        comp.diffEntry.previousCode = 'ABC';
        comp.diffEntry.code = 'DEF';
        comp.ngOnInit();

        expect(comp.editorNow.getEditor().setOptions).toHaveBeenCalledTimes(1);
        expect(comp.editorNow.getEditor().setOptions).toHaveBeenCalledWith({
            animatedScroll: true,
            maxLines: Infinity,
        });
        expect(comp.editorPrevious.getEditor().setOptions).toHaveBeenCalledTimes(1);
        expect(comp.editorPrevious.getEditor().setOptions).toHaveBeenCalledWith({
            animatedScroll: true,
            maxLines: Infinity,
        });

        expect(comp.editorNow.getEditor().getSession().setValue).toHaveBeenCalledTimes(1);
        expect(comp.editorNow.getEditor().getSession().setValue).toHaveBeenCalledWith('DEF');
        expect(comp.editorPrevious.getEditor().getSession().setValue).toHaveBeenCalledTimes(1);
        expect(comp.editorPrevious.getEditor().getSession().setValue).toHaveBeenCalledWith('ABC');

        expect(comp.editorNow.getEditor().container.style.background).toBe('rgba(63, 185, 80, 0.5)');
        expect(comp.editorPrevious.getEditor().container.style.background).toBe('rgba(248, 81, 73, 0.5)');
    });
});
