import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { CodeEditorRepositoryFileService } from 'app/programming/shared/code-editor/services/code-editor-repository.service';
import { CodeEditorFileBrowserDeleteComponent } from 'app/programming/manage/code-editor/file-browser/delete/code-editor-file-browser-delete';
import { DeleteFileChange, FileChange, FileType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('CodeEditorFileBrowserDeleteComponent', () => {
    let fixture: ComponentFixture<CodeEditorFileBrowserDeleteComponent>;
    let comp: CodeEditorFileBrowserDeleteComponent;
    let repositoryFileService: { deleteFile: ReturnType<typeof vi.fn> };
    let deleted: FileChange[];

    beforeEach(async () => {
        repositoryFileService = { deleteFile: vi.fn().mockReturnValue(of(undefined)) };

        await TestBed.configureTestingModule({
            imports: [CodeEditorFileBrowserDeleteComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .overrideComponent(CodeEditorFileBrowserDeleteComponent, {
                set: {
                    providers: [{ provide: CodeEditorRepositoryFileService, useValue: repositoryFileService }],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(CodeEditorFileBrowserDeleteComponent);
        comp = fixture.componentInstance;
        deleted = [];
        comp.fileDeleted.subscribe((change) => deleted.push(change));
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    const open = (fileNameToDelete: string, fileType: FileType) => {
        fixture.componentRef.setInput('fileNameToDelete', fileNameToDelete);
        fixture.componentRef.setInput('fileType', fileType);
        fixture.componentRef.setInput('visible', true);
        fixture.detectChanges();
    };

    it('should delete a file, close the dialog and report the change', () => {
        open('src/Test.java', FileType.FILE);

        comp.deleteFile();

        expect(repositoryFileService.deleteFile).toHaveBeenCalledOnce();
        expect(repositoryFileService.deleteFile).toHaveBeenCalledWith('src/Test.java');
        expect(deleted).toEqual([new DeleteFileChange(FileType.FILE, 'src/Test.java')]);
        expect(comp.visible()).toBe(false);
        expect(comp.isLoading()).toBe(false);
    });

    it('should close the dialog without deleting for problem statement', () => {
        open('ignored', FileType.PROBLEM_STATEMENT);

        comp.deleteFile();

        expect(repositoryFileService.deleteFile).not.toHaveBeenCalled();
        expect(deleted).toEqual([]);
        expect(comp.visible()).toBe(false);
    });

    it('should block a second click while the file is being deleted', () => {
        const pending = new Subject<void>();
        repositoryFileService.deleteFile.mockReturnValue(pending);
        open('src/Test.java', FileType.FILE);

        comp.deleteFile();

        expect(comp.isLoading()).toBe(true);
        expect(comp.visible()).toBe(true);
        pending.next();
        expect(comp.isLoading()).toBe(false);
        expect(comp.visible()).toBe(false);
    });

    it('should keep the dialog open when deleting fails', () => {
        repositoryFileService.deleteFile.mockReturnValue(throwError(() => new Error('failed')));
        open('src/Test.java', FileType.FILE);

        comp.deleteFile();

        expect(deleted).toEqual([]);
        expect(comp.visible()).toBe(true);
        expect(comp.isLoading()).toBe(false);
    });

    it('should not delete without a file name', () => {
        fixture.componentRef.setInput('fileType', FileType.FILE);
        fixture.detectChanges();

        comp.deleteFile();

        expect(repositoryFileService.deleteFile).not.toHaveBeenCalled();
    });

    it('should close the dialog on cancel', () => {
        open('folder', FileType.FOLDER);

        comp.closeModal();

        expect(comp.visible()).toBe(false);
        expect(repositoryFileService.deleteFile).not.toHaveBeenCalled();
    });
});
