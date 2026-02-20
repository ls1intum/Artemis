import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { CodeEditorFileBrowserFileComponent } from 'app/programming/manage/code-editor/file-browser/file/code-editor-file-browser-file.component';
import { TreeViewItem } from 'app/programming/shared/code-editor/treeview/models/tree-view-item';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

/**
 * Tests for CodeEditorFileBrowserNodeComponent abstract class
 * Testing through the concrete CodeEditorFileBrowserFileComponent implementation
 */
describe('CodeEditorFileBrowserNodeComponent', () => {
    let fixture: ComponentFixture<CodeEditorFileBrowserFileComponent>;
    let comp: CodeEditorFileBrowserFileComponent;

    const mockItem = new TreeViewItem<string>({
        value: 'test.ts',
        text: 'test.ts',
        children: [],
    });

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [CodeEditorFileBrowserFileComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(CodeEditorFileBrowserFileComponent);
        comp = fixture.componentInstance;
        fixture.componentRef.setInput('item', mockItem);
        fixture.componentRef.setInput('disableActions', false);
        fixture.detectChanges();
    });

    describe('isBeingRenamed', () => {
        it('should focus input when isBeingRenamed changes to true', async () => {
            fixture.componentRef.setInput('isBeingRenamed', true);
            fixture.detectChanges();
            const mockElement = { focus: jest.fn() };
            comp.renamingInput = { nativeElement: mockElement } as any;
            await new Promise((resolve) => setTimeout(resolve, 0));

            expect(mockElement.focus).toHaveBeenCalledOnce();
        });

        it('should not focus input when isBeingRenamed changes to false', fakeAsync(() => {
            const mockElement = { focus: jest.fn() };
            comp.renamingInput = { nativeElement: mockElement } as any;
            fixture.componentRef.setInput('isBeingRenamed', false);
            fixture.detectChanges();
            tick(10);

            expect(mockElement.focus).not.toHaveBeenCalled();
        }));

        it('should handle missing renamingInput', fakeAsync(() => {
            comp.renamingInput = undefined as any;

            expect(() => {
                fixture.componentRef.setInput('isBeingRenamed', true);
                fixture.detectChanges();
                tick(10);
            }).not.toThrow();
        }));
    });

    describe('setRenamingNode', () => {
        it('should emit onSetRenamingNode and stop propagation', () => {
            const emitSpy = jest.spyOn(comp.onSetRenamingNode, 'emit');
            const event = { stopPropagation: jest.fn() };

            comp.setRenamingNode(event);

            expect(event.stopPropagation).toHaveBeenCalledOnce();
            expect(emitSpy).toHaveBeenCalledWith(mockItem);
        });
    });

    describe('clearRenamingNode', () => {
        it('should emit onClearRenamingNode and stop propagation', () => {
            const emitSpy = jest.spyOn(comp.onClearRenamingNode, 'emit');
            const event = { stopPropagation: jest.fn() };

            comp.clearRenamingNode(event);

            expect(event.stopPropagation).toHaveBeenCalledOnce();
            expect(emitSpy).toHaveBeenCalled();
        });
    });

    describe('renameNode', () => {
        it('should emit onRenameNode when value is different and isBeingRenamed', () => {
            const emitSpy = jest.spyOn(comp.onRenameNode, 'emit');
            fixture.componentRef.setInput('isBeingRenamed', true);
            fixture.detectChanges();
            const event = { target: { value: 'newName.ts' } };

            comp.renameNode(event);

            expect(emitSpy).toHaveBeenCalledWith('newName.ts');
        });

        it('should not emit when value is empty', () => {
            const emitSpy = jest.spyOn(comp.onRenameNode, 'emit');
            fixture.componentRef.setInput('isBeingRenamed', true);
            fixture.detectChanges();
            const event = { target: { value: '' } };

            comp.renameNode(event);

            expect(emitSpy).not.toHaveBeenCalled();
        });

        it('should not emit when isBeingRenamed is false', () => {
            const emitSpy = jest.spyOn(comp.onRenameNode, 'emit');
            fixture.componentRef.setInput('isBeingRenamed', false);
            fixture.detectChanges();
            const event = { target: { value: 'newName.ts' } };

            comp.renameNode(event);

            expect(emitSpy).not.toHaveBeenCalled();
        });

        it('should emit onClearRenamingNode when value equals item text', () => {
            const clearSpy = jest.spyOn(comp.onClearRenamingNode, 'emit');
            const renameSpy = jest.spyOn(comp.onRenameNode, 'emit');
            fixture.componentRef.setInput('isBeingRenamed', true);
            fixture.detectChanges();
            const event = { target: { value: 'test.ts' } };

            comp.renameNode(event);

            expect(clearSpy).toHaveBeenCalled();
            expect(renameSpy).not.toHaveBeenCalled();
        });
    });

    describe('deleteNode', () => {
        it('should emit onDeleteNode and stop propagation', () => {
            const emitSpy = jest.spyOn(comp.onDeleteNode, 'emit');
            const event = { stopPropagation: jest.fn() };

            comp.deleteNode(event);

            expect(event.stopPropagation).toHaveBeenCalledOnce();
            expect(emitSpy).toHaveBeenCalledWith(mockItem);
        });
    });

    describe('inputs', () => {
        it('should have default values for boolean inputs', () => {
            expect(comp.hasError()).toBeFalse();
            expect(comp.hasUnsavedChanges()).toBeFalse();
            expect(comp.isBeingRenamed()).toBeFalse();
        });

        it('should accept item input', () => {
            const newItem = new TreeViewItem<string>({
                value: 'new.ts',
                text: 'new.ts',
                children: [],
            });

            fixture.componentRef.setInput('item', newItem);
            fixture.detectChanges();

            expect(comp.item()).toEqual(newItem);
        });
    });
});
