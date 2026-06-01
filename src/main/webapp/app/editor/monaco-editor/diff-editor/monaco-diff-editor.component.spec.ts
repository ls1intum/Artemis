import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { vi } from 'vitest';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';
import { MonacoDiffEditorComponent } from 'app/editor/monaco-editor/diff-editor/monaco-diff-editor.component';
import { ThemeService } from 'app/core/theme/shared/theme.service';
import { MockThemeService } from 'test/helpers/mocks/service/mock-theme.service';

// Capture the global ResizeObserver provided by the test setup so it can be restored after each test.
const originalResizeObserver = globalThis.ResizeObserver;

describe('MonacoDiffEditorComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<MonacoDiffEditorComponent>;
    let comp: MonacoDiffEditorComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MonacoDiffEditorComponent],
            providers: [{ provide: ThemeService, useClass: MockThemeService }],
        }).compileComponents();
        global.ResizeObserver = MockResizeObserver as unknown as typeof ResizeObserver;
        fixture = TestBed.createComponent(MonacoDiffEditorComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
        globalThis.ResizeObserver = originalResizeObserver;
    });

    it('should dispose its listeners and subscriptions when destroyed', () => {
        fixture.detectChanges();
        const listenerDisposeSpies = comp.listeners.map((listener) => vi.spyOn(listener, 'dispose'));
        comp.ngOnDestroy();
        for (const spy of listenerDisposeSpies) {
            expect(spy).toHaveBeenCalledOnce();
        }
    });

    it('should update the size of its container', () => {
        fixture.detectChanges();
        const element = document.createElement('div');
        comp.monacoDiffEditorContainerElement = element;
        comp.adjustContainerHeight(100);
        expect(element.style.height).toBe('100px');
    });

    it('should keep the split view enabled when allowed and honor toggling it off', () => {
        const updateOptionsSpy = vi.spyOn(comp['_editor'], 'updateOptions');
        fixture.componentRef.setInput('allowSplitView', true);
        fixture.componentRef.setInput('forceSideBySide', false);
        fixture.detectChanges();
        // An explicitly enabled split view must not collapse to inline (unified) in a narrow container.
        expect(updateOptionsSpy).toHaveBeenCalledWith(expect.objectContaining({ renderSideBySide: true, useInlineViewWhenSpaceIsLimited: false }));

        updateOptionsSpy.mockClear();
        fixture.componentRef.setInput('allowSplitView', false);
        fixture.detectChanges();
        expect(updateOptionsSpy).toHaveBeenCalledWith(expect.objectContaining({ renderSideBySide: false, useInlineViewWhenSpaceIsLimited: true }));
    });

    it('should set the text of the editor', () => {
        const original = 'some original content';
        const modified = 'some modified content';
        fixture.detectChanges();
        comp.setFileContents(original, modified, 'originalFileName.java', 'modifiedFileName.java');
        expect(comp.getText()).toEqual({ original, modified });
    });

    it('should set file contents with undefined parameters (testing nullish coalescing operators)', () => {
        fixture.detectChanges();
        // Test all the ?? fallbacks in setFileContents
        comp.setFileContents(undefined, undefined, undefined, undefined);
        const result = comp.getText();
        expect(result.original).toBe('');
        expect(result.modified).toBe('');
    });

    it('should set file contents with null parameters', () => {
        fixture.detectChanges();
        // Test nullish coalescing with null values
        comp.setFileContents(null!, null!, null!, null!);
        const result = comp.getText();
        expect(result.original).toBe('');
        expect(result.modified).toBe('');
    });

    it('should handle missing original filename and use fallback', () => {
        fixture.detectChanges();
        comp.setFileContents('content', 'content', undefined, 'modified.java');
        // This should work without errors, using 'left' as fallback for original filename
        expect(comp.getText()).toEqual({ original: 'content', modified: 'content' });
    });

    it('should handle missing modified filename and use fallback', () => {
        fixture.detectChanges();
        comp.setFileContents('content', 'content', 'original.java', undefined);
        expect(comp.getText()).toEqual({ original: 'content', modified: 'content' });
    });

    it('should handle getLineChanges returning null in setupDiffListener', () => {
        fixture.detectChanges();

        // Mock getLineChanges to return null to test the ?? [] fallback
        const mockEditor = comp['_editor'];
        vi.spyOn(mockEditor, 'getLineChanges').mockReturnValue(null as any);

        const readyCallbackStub = vi.fn();
        comp.onReadyForDisplayChange.subscribe(readyCallbackStub);

        // Trigger the diff listener (fired synchronously by the mock when the model is set).
        comp.setFileContents('original', 'modified');

        // Should handle null getLineChanges gracefully
        expect(readyCallbackStub).toHaveBeenCalled();
    });

    it('should notify about its readiness to display', () => {
        const readyCallbackStub = vi.fn();
        fixture.detectChanges();
        // The mock diff editor does not compute real diffs; provide a synthetic single-line change so the
        // converted LineChange is deterministic (1 added, 1 removed).
        vi.spyOn(comp['_editor'], 'getLineChanges').mockReturnValue([
            { originalStartLineNumber: 1, originalEndLineNumber: 1, modifiedStartLineNumber: 1, modifiedEndLineNumber: 1, charChanges: undefined },
        ] as any);
        comp.onReadyForDisplayChange.subscribe(readyCallbackStub);
        comp.setFileContents('original', 'file', 'modified', 'file');
        expect(readyCallbackStub).toHaveBeenCalledTimes(2);
        expect(readyCallbackStub).toHaveBeenNthCalledWith(1, { ready: false, lineChange: { addedLineCount: 0, removedLineCount: 0 } });
        expect(readyCallbackStub).toHaveBeenNthCalledWith(2, { ready: true, lineChange: { addedLineCount: 1, removedLineCount: 1 } });
    });
});
