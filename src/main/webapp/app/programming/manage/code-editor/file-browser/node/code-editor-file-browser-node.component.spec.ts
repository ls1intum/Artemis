import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ElementRef, Signal } from '@angular/core';
import { CodeEditorFileBrowserFileComponent } from 'app/programming/manage/code-editor/file-browser/file/code-editor-file-browser-file.component';
import { TreeViewItem } from 'app/programming/shared/code-editor/treeview/models/tree-view-item';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

/**
 * Typed view onto the `renamingInput` viewChild signal so the spec can read the resolved
 * ElementRef without a blanket `(comp as any)` cast. The shape mirrors the component declaration.
 */
type NodeInternals = CodeEditorFileBrowserFileComponent & {
    renamingInput: Signal<ElementRef | undefined>;
};
const internals = (c: CodeEditorFileBrowserFileComponent): NodeInternals => c as NodeInternals;

/**
 * Tests for CodeEditorFileBrowserNodeComponent abstract class
 * Testing through the concrete CodeEditorFileBrowserFileComponent implementation
 */
describe('CodeEditorFileBrowserNodeComponent', () => {
    setupTestBed({ zoneless: true });

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
        });

        fixture = TestBed.createComponent(CodeEditorFileBrowserFileComponent);
        comp = fixture.componentInstance;
        fixture.componentRef.setInput('item', mockItem);
        fixture.componentRef.setInput('disableActions', false);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
        vi.useRealTimers();
    });

    describe('isBeingRenamed', () => {
        it('should focus input when isBeingRenamed changes to true', () => {
            vi.useFakeTimers();
            fixture.componentRef.setInput('isBeingRenamed', true);
            fixture.detectChanges();

            // When renaming, the concrete template renders the #renamingInput element which the
            // viewChild signal resolves to. The effect's setTimeout(0) then focuses it.
            const renamingInput = internals(comp).renamingInput();
            expect(renamingInput).toBeDefined();
            const focusSpy = vi.spyOn(renamingInput!.nativeElement as HTMLElement, 'focus');

            vi.runAllTimers();

            expect(focusSpy).toHaveBeenCalledOnce();
        });

        it('should not focus input when isBeingRenamed changes to false', () => {
            vi.useFakeTimers();
            const focusSpy = vi.spyOn(HTMLElement.prototype, 'focus');
            fixture.componentRef.setInput('isBeingRenamed', false);
            fixture.detectChanges();

            // No #renamingInput is rendered when not renaming, so nothing is focused.
            expect(internals(comp).renamingInput()).toBeUndefined();

            vi.runAllTimers();

            expect(focusSpy).not.toHaveBeenCalled();
        });

        it('should handle missing renamingInput', () => {
            vi.useFakeTimers();

            // isBeingRenamed stays false from beforeEach, so the viewChild never resolves; the
            // effect's guard must short-circuit without throwing.
            expect(internals(comp).renamingInput()).toBeUndefined();
            expect(() => {
                fixture.componentRef.setInput('isBeingRenamed', false);
                fixture.detectChanges();
                vi.runAllTimers();
            }).not.toThrow();
        });
    });

    describe('setRenamingNode', () => {
        it('should emit onSetRenamingNode and stop propagation', () => {
            const emitSpy = vi.spyOn(comp.onSetRenamingNode, 'emit');
            const event = { stopPropagation: vi.fn() };

            comp.setRenamingNode(event);

            expect(event.stopPropagation).toHaveBeenCalledOnce();
            expect(emitSpy).toHaveBeenCalledWith(mockItem);
        });
    });

    describe('clearRenamingNode', () => {
        it('should emit onClearRenamingNode and stop propagation', () => {
            const emitSpy = vi.spyOn(comp.onClearRenamingNode, 'emit');
            const event = { stopPropagation: vi.fn() };

            comp.clearRenamingNode(event);

            expect(event.stopPropagation).toHaveBeenCalledOnce();
            expect(emitSpy).toHaveBeenCalled();
        });
    });

    describe('renameNode', () => {
        it('should emit onRenameNode when value is different and isBeingRenamed', () => {
            const emitSpy = vi.spyOn(comp.onRenameNode, 'emit');
            fixture.componentRef.setInput('isBeingRenamed', true);
            fixture.detectChanges();
            const event = { target: { value: 'newName.ts' } };

            comp.renameNode(event);

            expect(emitSpy).toHaveBeenCalledWith('newName.ts');
        });

        it('should not emit when value is empty', () => {
            const emitSpy = vi.spyOn(comp.onRenameNode, 'emit');
            fixture.componentRef.setInput('isBeingRenamed', true);
            fixture.detectChanges();
            const event = { target: { value: '' } };

            comp.renameNode(event);

            expect(emitSpy).not.toHaveBeenCalled();
        });

        it('should not emit when isBeingRenamed is false', () => {
            const emitSpy = vi.spyOn(comp.onRenameNode, 'emit');
            fixture.componentRef.setInput('isBeingRenamed', false);
            fixture.detectChanges();
            const event = { target: { value: 'newName.ts' } };

            comp.renameNode(event);

            expect(emitSpy).not.toHaveBeenCalled();
        });

        it('should emit onClearRenamingNode when value equals item text', () => {
            const clearSpy = vi.spyOn(comp.onClearRenamingNode, 'emit');
            const renameSpy = vi.spyOn(comp.onRenameNode, 'emit');
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
            const emitSpy = vi.spyOn(comp.onDeleteNode, 'emit');
            const event = { stopPropagation: vi.fn() };

            comp.deleteNode(event);

            expect(event.stopPropagation).toHaveBeenCalledOnce();
            expect(emitSpy).toHaveBeenCalledWith(mockItem);
        });
    });

    describe('inputs', () => {
        it('should have default values for boolean inputs', () => {
            expect(comp.hasError()).toBe(false);
            expect(comp.hasUnsavedChanges()).toBe(false);
            expect(comp.isBeingRenamed()).toBe(false);
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
