import { LineSelectionCallbacks, LineSelectionDisposable, LineSelectionOptions, setupLineSelectionHandler } from './line-selection-handler';
import * as monaco from 'monaco-editor';

describe('LineSelectionHandler', () => {
    let mockEditor: jest.Mocked<monaco.editor.IStandaloneCodeEditor>;
    let callbacks: LineSelectionCallbacks;
    let options: LineSelectionOptions;
    let disposable: LineSelectionDisposable;
    let mockDomNode: HTMLElement;
    let mockDecorationsCollection: jest.Mocked<monaco.editor.IEditorDecorationsCollection>;
    let selectionChangeCallback: (() => void) | undefined;
    let scrollChangeCallback: (() => void) | undefined;
    let blurCallback: (() => void) | undefined;

    beforeEach(() => {
        jest.useFakeTimers();

        mockDecorationsCollection = {
            set: jest.fn(),
            clear: jest.fn(),
            append: jest.fn(),
            getRanges: jest.fn(),
            has: jest.fn(),
            length: 0,
            getRange: jest.fn(),
            onDidChange: jest.fn(),
        } as unknown as jest.Mocked<monaco.editor.IEditorDecorationsCollection>;

        mockDomNode = document.createElement('div');
        const marginDiv = document.createElement('div');
        marginDiv.className = 'margin';
        mockDomNode.appendChild(marginDiv);

        mockEditor = {
            getSelection: jest.fn(),
            createDecorationsCollection: jest.fn().mockReturnValue(mockDecorationsCollection),
            getDomNode: jest.fn().mockReturnValue(mockDomNode),
            getOption: jest.fn().mockReturnValue(20), // lineHeight
            getTopForLineNumber: jest.fn().mockReturnValue(100),
            getScrollTop: jest.fn().mockReturnValue(0),
            hasTextFocus: jest.fn().mockReturnValue(false),
            onDidChangeCursorSelection: jest.fn((cb) => {
                selectionChangeCallback = cb;
                return { dispose: jest.fn() };
            }),
            onDidScrollChange: jest.fn((cb) => {
                scrollChangeCallback = cb;
                return { dispose: jest.fn() };
            }),
            onDidBlurEditorText: jest.fn((cb) => {
                blurCallback = cb;
                return { dispose: jest.fn() };
            }),
        } as unknown as jest.Mocked<monaco.editor.IStandaloneCodeEditor>;

        callbacks = {
            onAddComment: jest.fn(),
        };

        options = {
            addButtonTooltip: 'Add inline comment',
        };
    });

    afterEach(() => {
        disposable?.dispose();
        jest.useRealTimers();
        jest.clearAllMocks();
    });

    it('should setup event listeners on initialization', () => {
        disposable = setupLineSelectionHandler(mockEditor, callbacks, options);

        expect(mockEditor.onDidChangeCursorSelection).toHaveBeenCalled();
        expect(mockEditor.onDidScrollChange).toHaveBeenCalled();
        expect(mockEditor.onDidBlurEditorText).toHaveBeenCalled();
    });

    it('should return disposable that cleans up resources', () => {
        disposable = setupLineSelectionHandler(mockEditor, callbacks, options);

        expect(disposable.dispose).toBeDefined();
        disposable.dispose();
    });

    it('should clear decorations when selection is empty', () => {
        mockEditor.getSelection.mockReturnValue(null);
        disposable = setupLineSelectionHandler(mockEditor, callbacks, options);

        // Trigger selection change
        selectionChangeCallback?.();

        // Decorations should not be created when no selection
        expect(mockDecorationsCollection.set).not.toHaveBeenCalled();
    });

    it('should create decorations when lines are selected', () => {
        const mockSelection = {
            isEmpty: () => false,
            startLineNumber: 5,
            endLineNumber: 10,
        } as monaco.Selection;
        mockEditor.getSelection.mockReturnValue(mockSelection);
        disposable = setupLineSelectionHandler(mockEditor, callbacks, options);

        // Trigger selection change
        selectionChangeCallback?.();

        expect(mockEditor.createDecorationsCollection).toHaveBeenCalled();
        expect(mockDecorationsCollection.set).toHaveBeenCalled();
    });

    it('should create add button in gutter when lines are selected', () => {
        const mockSelection = {
            isEmpty: () => false,
            startLineNumber: 1,
            endLineNumber: 3,
        } as monaco.Selection;
        mockEditor.getSelection.mockReturnValue(mockSelection);
        disposable = setupLineSelectionHandler(mockEditor, callbacks, options);

        // Trigger selection change
        selectionChangeCallback?.();

        const marginDiv = mockDomNode.querySelector('.margin');
        const addButton = marginDiv?.querySelector('.inline-comment-add-button');
        expect(addButton).toBeTruthy();
    });

    it('should call onAddComment callback when add button is clicked', () => {
        const mockSelection = {
            isEmpty: () => false,
            startLineNumber: 2,
            endLineNumber: 5,
        } as monaco.Selection;
        mockEditor.getSelection.mockReturnValue(mockSelection);
        disposable = setupLineSelectionHandler(mockEditor, callbacks, options);

        // Trigger selection change to create button
        selectionChangeCallback?.();

        const marginDiv = mockDomNode.querySelector('.margin');
        const addButton = marginDiv?.querySelector('.inline-comment-add-button') as HTMLElement;
        expect(addButton).toBeTruthy();

        // Click the button
        addButton.click();

        expect(callbacks.onAddComment).toHaveBeenCalledWith(2, 5);
    });

    it('should reposition add button on scroll', () => {
        const mockSelection = {
            isEmpty: () => false,
            startLineNumber: 3,
            endLineNumber: 4,
        } as monaco.Selection;
        mockEditor.getSelection.mockReturnValue(mockSelection);
        disposable = setupLineSelectionHandler(mockEditor, callbacks, options);

        // Trigger selection change to create button
        selectionChangeCallback?.();

        // Verify button exists
        const marginDiv = mockDomNode.querySelector('.margin');
        const addButton = marginDiv?.querySelector('.inline-comment-add-button') as HTMLElement;
        expect(addButton).toBeTruthy();

        // Change scroll position
        mockEditor.getScrollTop.mockReturnValue(50);
        mockEditor.getTopForLineNumber.mockReturnValue(150);

        // Trigger scroll change
        scrollChangeCallback?.();

        expect(addButton.style.top).toBe('100px');
    });

    it('should clear decorations and remove button on blur after timeout', () => {
        const mockSelection = {
            isEmpty: () => false,
            startLineNumber: 1,
            endLineNumber: 2,
        } as monaco.Selection;
        mockEditor.getSelection.mockReturnValue(mockSelection);
        mockEditor.hasTextFocus.mockReturnValue(false);
        disposable = setupLineSelectionHandler(mockEditor, callbacks, options);

        // Trigger selection change to create button
        selectionChangeCallback?.();

        // Verify button exists
        const marginDiv = mockDomNode.querySelector('.margin');
        const addButton = marginDiv?.querySelector('.inline-comment-add-button');
        expect(addButton).toBeTruthy();

        // Trigger blur
        blurCallback?.();

        // Fast-forward timer
        jest.advanceTimersByTime(200);

        expect(mockDecorationsCollection.clear).toHaveBeenCalled();
    });

    it('should not clear decorations on blur if editor still has focus', () => {
        const mockSelection = {
            isEmpty: () => false,
            startLineNumber: 1,
            endLineNumber: 2,
        } as monaco.Selection;
        mockEditor.getSelection.mockReturnValue(mockSelection);
        mockEditor.hasTextFocus.mockReturnValue(true); // Editor still has focus
        disposable = setupLineSelectionHandler(mockEditor, callbacks, options);

        // Trigger selection change to create decorations
        selectionChangeCallback?.();

        // Trigger blur
        blurCallback?.();

        // Fast-forward timer
        jest.advanceTimersByTime(200);

        // Should not clear because editor still has focus
        expect(mockDecorationsCollection.clear).not.toHaveBeenCalled();
    });

    it('should handle missing DOM node gracefully', () => {
        mockEditor.getDomNode.mockReturnValue(null);
        const mockSelection = {
            isEmpty: () => false,
            startLineNumber: 1,
            endLineNumber: 2,
        } as monaco.Selection;
        mockEditor.getSelection.mockReturnValue(mockSelection);

        // Should not throw
        expect(() => {
            disposable = setupLineSelectionHandler(mockEditor, callbacks, options);
            selectionChangeCallback?.();
        }).not.toThrow();
    });

    it('should set add button tooltip from options', () => {
        const customOptions: LineSelectionOptions = {
            addButtonTooltip: 'Custom tooltip text',
        };
        const mockSelection = {
            isEmpty: () => false,
            startLineNumber: 1,
            endLineNumber: 1,
        } as monaco.Selection;
        mockEditor.getSelection.mockReturnValue(mockSelection);

        disposable = setupLineSelectionHandler(mockEditor, callbacks, customOptions);
        selectionChangeCallback?.();

        const marginDiv = mockDomNode.querySelector('.margin');
        const addButton = marginDiv?.querySelector('.inline-comment-add-button') as HTMLElement;
        expect(addButton?.title).toBe('Custom tooltip text');
    });
});
