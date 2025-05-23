import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';
import { MonacoDiffEditorComponent } from 'app/shared/monaco-editor/diff-editor/monaco-diff-editor.component';
import { ThemeService } from 'app/core/theme/shared/theme.service';
import { MockThemeService } from 'test/helpers/mocks/service/mock-theme.service';
import * as monaco from 'monaco-editor';

describe('MonacoDiffEditorComponent', () => {
    let fixture: ComponentFixture<MonacoDiffEditorComponent>;
    let comp: MonacoDiffEditorComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MonacoDiffEditorComponent],
            providers: [{ provide: ThemeService, useClass: MockThemeService }],
        })
            .compileComponents()
            .then(() => {
                global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
                    return new MockResizeObserver(callback);
                });
                fixture = TestBed.createComponent(MonacoDiffEditorComponent);
                comp = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should dispose its listeners and subscriptions when destroyed', () => {
        fixture.detectChanges();
        const listenerDisposeSpies = comp.listeners.map((listener) => jest.spyOn(listener, 'dispose'));
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
        // This should work without errors, using 'right' as fallback for modified filename
        expect(comp.getText()).toEqual({ original: 'content', modified: 'content' });
    });

    it('should convert monaco line changes correctly', () => {
        fixture.detectChanges();
        const mockLineChanges: monaco.editor.ILineChange[] = [
            {
                originalStartLineNumber: 1,
                originalEndLineNumber: 2,
                modifiedStartLineNumber: 1,
                modifiedEndLineNumber: 3,
                charChanges: undefined,
            },
        ];

        const result = comp.convertMonacoLineChanges(mockLineChanges);
        expect(result.addedLineCount).toBe(3); // 3 - 1 + 1 = 3
        expect(result.removedLineCount).toBe(2); // 2 - 1 + 1 = 2
    });

    it('should handle null monaco line changes', () => {
        fixture.detectChanges();
        // Test the if (!monacoLineChanges) branch
        const result = comp.convertMonacoLineChanges(null!);
        expect(result.addedLineCount).toBe(0);
        expect(result.removedLineCount).toBe(0);
    });

    it('should handle undefined monaco line changes', () => {
        fixture.detectChanges();
        // Test the if (!monacoLineChanges) branch with undefined
        const result = comp.convertMonacoLineChanges(undefined!);
        expect(result.addedLineCount).toBe(0);
        expect(result.removedLineCount).toBe(0);
    });

    it('should handle empty monaco line changes array', () => {
        fixture.detectChanges();
        const result = comp.convertMonacoLineChanges([]);
        expect(result.addedLineCount).toBe(0);
        expect(result.removedLineCount).toBe(0);
    });

    it('should handle edge case where modified end line is less than start line', () => {
        fixture.detectChanges();
        const mockLineChanges: monaco.editor.ILineChange[] = [
            {
                originalStartLineNumber: 1,
                originalEndLineNumber: 2,
                modifiedStartLineNumber: 5,
                modifiedEndLineNumber: 3, // End line < start line
                charChanges: undefined,
            },
        ];

        const result = comp.convertMonacoLineChanges(mockLineChanges);
        expect(result.addedLineCount).toBe(0); // Should use the ternary fallback : 0
        expect(result.removedLineCount).toBe(2);
    });

    it('should handle edge case where original end line is less than start line', () => {
        fixture.detectChanges();
        const mockLineChanges: monaco.editor.ILineChange[] = [
            {
                originalStartLineNumber: 5,
                originalEndLineNumber: 3, // End line < start line
                modifiedStartLineNumber: 1,
                modifiedEndLineNumber: 2,
                charChanges: undefined,
            },
        ];

        const result = comp.convertMonacoLineChanges(mockLineChanges);
        expect(result.addedLineCount).toBe(2);
        expect(result.removedLineCount).toBe(0); // Should use the ternary fallback : 0
    });

    it('should handle getLineChanges returning null in setupDiffListener', async () => {
        fixture.detectChanges();

        // Mock getLineChanges to return null to test the ?? [] fallback
        const mockEditor = comp['_editor'];
        jest.spyOn(mockEditor, 'getLineChanges').mockReturnValue(null);

        const readyCallbackStub = jest.fn();
        comp.onReadyForDisplayChange.subscribe(readyCallbackStub);

        // Trigger the diff listener by calling the internal method that would be called
        comp.setFileContents('original', 'modified');

        // Wait for async operations
        await new Promise((r) => setTimeout(r, 100));

        // Should handle null getLineChanges gracefully
        expect(readyCallbackStub).toHaveBeenCalled();
    });

    it('should notify about its readiness to display', async () => {
        const readyCallbackStub = jest.fn();
        comp.onReadyForDisplayChange.subscribe(readyCallbackStub);
        fixture.detectChanges();
        comp.setFileContents('original', 'file', 'modified', 'file');
        // Wait for the diff computation, which is handled by Monaco.
        await new Promise((r) => setTimeout(r, 200));
        expect(readyCallbackStub).toHaveBeenCalledTimes(2);
        expect(readyCallbackStub).toHaveBeenNthCalledWith(1, { ready: false, lineChange: { addedLineCount: 0, removedLineCount: 0 } });
        expect(readyCallbackStub).toHaveBeenNthCalledWith(2, { ready: true, lineChange: { addedLineCount: 1, removedLineCount: 1 } });
    });
});
