import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CodeEditorFileBrowserCreateNodeComponent } from 'app/programming/manage/code-editor/file-browser/create-node/code-editor-file-browser-create-node.component';
import { FileType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { faFile, faFolder } from '@fortawesome/free-solid-svg-icons';

describe('CodeEditorFileBrowserCreateNodeComponent', () => {
    let fixture: ComponentFixture<CodeEditorFileBrowserCreateNodeComponent>;
    let comp: CodeEditorFileBrowserCreateNodeComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [CodeEditorFileBrowserCreateNodeComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(CodeEditorFileBrowserCreateNodeComponent);
        comp = fixture.componentInstance;
        comp.createFileType = FileType.FILE;
        comp.folder = '/src';
    });

    it('should create', () => {
        expect(comp).toBeTruthy();
    });

    it('should have correct icons', () => {
        expect(comp.faFile).toEqual(faFile);
        expect(comp.faFolder).toEqual(faFolder);
    });

    it('should expose FileType enum', () => {
        expect(comp.FileType).toBe(FileType);
    });

    describe('createFile', () => {
        it('should emit onCreateFile when value is provided', () => {
            const emitSpy = jest.spyOn(comp.onCreateFile, 'emit');
            const event = { target: { value: 'newFile.ts' } };

            comp.createFile(event);

            expect(emitSpy).toHaveBeenCalledWith('newFile.ts');
        });

        it('should emit onClearCreatingFile when value is empty', () => {
            const clearSpy = jest.spyOn(comp.onClearCreatingFile, 'emit');
            const createSpy = jest.spyOn(comp.onCreateFile, 'emit');
            const event = { target: { value: '' } };

            comp.createFile(event);

            expect(clearSpy).toHaveBeenCalledWith(event);
            expect(createSpy).not.toHaveBeenCalled();
        });

        it('should emit onClearCreatingFile when value is undefined', () => {
            const clearSpy = jest.spyOn(comp.onClearCreatingFile, 'emit');
            const event = { target: { value: undefined } };

            comp.createFile(event);

            expect(clearSpy).toHaveBeenCalled();
        });
    });

    describe('ngAfterViewInit', () => {
        it('should focus the input element', () => {
            const mockElement = { focus: jest.fn() };
            comp.creatingInput = { nativeElement: mockElement } as any;

            comp.ngAfterViewInit();

            expect(mockElement.focus).toHaveBeenCalledOnce();
        });
    });

    describe('inputs', () => {
        it('should accept createFileType input', () => {
            comp.createFileType = FileType.FOLDER;
            expect(comp.createFileType).toBe(FileType.FOLDER);
        });

        it('should accept folder input', () => {
            comp.folder = '/test/folder';
            expect(comp.folder).toBe('/test/folder');
        });
    });
});
