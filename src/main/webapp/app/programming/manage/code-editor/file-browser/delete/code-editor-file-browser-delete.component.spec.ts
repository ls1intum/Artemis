import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { of } from 'rxjs';
import { CodeEditorRepositoryFileService } from 'app/programming/shared/code-editor/services/code-editor-repository.service';
import { CodeEditorFileBrowserDeleteComponent } from 'app/programming/manage/code-editor/file-browser/delete/code-editor-file-browser-delete';
import { DeleteFileChange, FileType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('CodeEditorFileBrowserDeleteComponent', () => {
    let fixture: ComponentFixture<CodeEditorFileBrowserDeleteComponent>;
    let comp: CodeEditorFileBrowserDeleteComponent;
    let activeModal: { dismiss: jest.Mock };
    let repositoryFileService: { deleteFile: jest.Mock };

    beforeEach(async () => {
        activeModal = { dismiss: jest.fn() };
        repositoryFileService = { deleteFile: jest.fn().mockReturnValue(of(undefined)) };

        await TestBed.configureTestingModule({
            imports: [CodeEditorFileBrowserDeleteComponent],
        })
            .overrideComponent(CodeEditorFileBrowserDeleteComponent, {
                set: {
                    providers: [
                        { provide: NgbActiveModal, useValue: activeModal },
                        { provide: CodeEditorRepositoryFileService, useValue: repositoryFileService },
                        { provide: TranslateService, useClass: MockTranslateService },
                    ],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(CodeEditorFileBrowserDeleteComponent);
        comp = fixture.componentInstance;
    });

    it('should delete a file and notify the parent', () => {
        const parent = { onFileDeleted: jest.fn() };
        comp.setInputs({ fileNameToDelete: 'src/Test.java', parent, fileType: FileType.FILE });
        fixture.detectChanges();

        comp.deleteFile();

        expect(repositoryFileService.deleteFile).toHaveBeenCalledOnce();
        expect(repositoryFileService.deleteFile).toHaveBeenCalledWith('src/Test.java');
        expect(parent.onFileDeleted).toHaveBeenCalledWith(new DeleteFileChange(FileType.FILE, 'src/Test.java'));
        expect(activeModal.dismiss).toHaveBeenCalledWith('cancel');
    });

    it('should close the modal without deleting for problem statement', () => {
        const parent = { onFileDeleted: jest.fn() };
        comp.setInputs({ fileNameToDelete: 'ignored', parent, fileType: FileType.PROBLEM_STATEMENT });
        fixture.detectChanges();

        comp.deleteFile();

        expect(repositoryFileService.deleteFile).not.toHaveBeenCalled();
        expect(parent.onFileDeleted).not.toHaveBeenCalled();
        expect(activeModal.dismiss).toHaveBeenCalledWith('cancel');
    });
});
