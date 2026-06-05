import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CodeEditorFileBrowserCreateNodeComponent } from 'app/programming/manage/code-editor/file-browser/create-node/code-editor-file-browser-create-node.component';
import { FileType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { faFile, faFolder } from '@fortawesome/free-solid-svg-icons';

describe('CodeEditorFileBrowserCreateNodeComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<CodeEditorFileBrowserCreateNodeComponent>;
    let comp: CodeEditorFileBrowserCreateNodeComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [CodeEditorFileBrowserCreateNodeComponent],
        });

        fixture = TestBed.createComponent(CodeEditorFileBrowserCreateNodeComponent);
        comp = fixture.componentInstance;
        fixture.componentRef.setInput('createFileType', FileType.FILE);
        fixture.componentRef.setInput('folder', '/src');
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
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
            const emitSpy = vi.spyOn(comp.onCreateFile, 'emit');
            const event = { target: { value: 'newFile.ts' } };

            comp.createFile(event);

            expect(emitSpy).toHaveBeenCalledWith('newFile.ts');
        });

        it('should emit onClearCreatingFile when value is empty', () => {
            const clearSpy = vi.spyOn(comp.onClearCreatingFile, 'emit');
            const createSpy = vi.spyOn(comp.onCreateFile, 'emit');
            const event = { target: { value: '' } };

            comp.createFile(event);

            expect(clearSpy).toHaveBeenCalledWith(event);
            expect(createSpy).not.toHaveBeenCalled();
        });

        it('should emit onClearCreatingFile when value is undefined', () => {
            const clearSpy = vi.spyOn(comp.onClearCreatingFile, 'emit');
            const event = { target: { value: undefined } };

            comp.createFile(event);

            expect(clearSpy).toHaveBeenCalled();
        });
    });

    describe('ngAfterViewInit', () => {
        it('should focus the input element', () => {
            const inputElement = comp.creatingInput().nativeElement as HTMLInputElement;
            const focusSpy = vi.spyOn(inputElement, 'focus');

            comp.ngAfterViewInit();

            expect(focusSpy).toHaveBeenCalledOnce();
        });
    });

    describe('inputs', () => {
        it('should accept createFileType input', () => {
            fixture.componentRef.setInput('createFileType', FileType.FOLDER);
            fixture.detectChanges();
            expect(comp.createFileType()).toBe(FileType.FOLDER);
        });

        it('should accept folder input', () => {
            fixture.componentRef.setInput('folder', '/test/folder');
            fixture.detectChanges();
            expect(comp.folder()).toBe('/test/folder');
        });
    });
});
