import { ReviewCommentWidgetManager, ReviewCommentWidgetManagerConfig } from 'app/exercise/review/review-comment-widget-manager';
import { ReviewCommentDraftWidgetComponent } from 'app/exercise/review/review-comment-draft-widget/review-comment-draft-widget.component';
import { ReviewCommentThreadWidgetComponent } from 'app/exercise/review/review-comment-thread-widget/review-comment-thread-widget.component';
import { CommentThread, CommentThreadLocationType } from 'app/exercise/shared/entities/review/comment-thread.model';
import { afterEach, describe, expect, it, vi } from 'vitest';

describe('ReviewCommentWidgetManager', () => {
    const createEditorMock = () => {
        let onDidScrollChangeCallback: (() => void) | undefined;
        let modelLines = [''];
        const model = {
            getLineCount: vi.fn(() => modelLines.length),
            getLineMaxColumn: vi.fn((lineNumber: number) => (modelLines[lineNumber - 1]?.length ?? 0) + 1),
            getValueInRange: vi.fn((range: { startLineNumber: number; endLineNumber: number }) => modelLines.slice(range.startLineNumber - 1, range.endLineNumber).join('\n')),
        };
        const activeEditor = {
            executeEdits: vi.fn(),
        };
        const monacoEditorMock = {
            onDidScrollChange: vi.fn((callback: () => void) => {
                onDidScrollChangeCallback = callback;
                return { dispose: vi.fn() };
            }),
        };
        return {
            setLineDecorationsHoverButton: vi.fn(),
            clearLineDecorationsHoverButton: vi.fn(),
            addLineWidget: vi.fn(),
            disposeWidgetsByPrefix: vi.fn(),
            getEditor: vi.fn(() => monacoEditorMock),
            getModel: vi.fn<() => typeof model | undefined>(() => model),
            getActiveEditor: vi.fn(() => activeEditor),
            triggerScroll: () => onDidScrollChangeCallback?.(),
            setModelLines: (lines: string[]) => {
                modelLines = lines;
            },
        };
    };

    const createDraftRef = () => {
        const submittedSubscription = { unsubscribe: vi.fn() };
        const cancelSubscription = { unsubscribe: vi.fn() };
        const instance: any = {
            onSubmitted: {
                subscribe: vi.fn((cb) => {
                    instance._onSubmitted = cb;
                    return submittedSubscription;
                }),
            },
            onCancel: {
                subscribe: vi.fn((cb) => {
                    instance._onCancel = cb;
                    return cancelSubscription;
                }),
            },
        };
        return {
            instance,
            submittedSubscription,
            cancelSubscription,
            location: { nativeElement: document.createElement('div') },
            setInput: vi.fn(),
            destroy: vi.fn(),
        } as any;
    };

    const createThreadRef = () => {
        const instance: any = {
            onToggleCollapse: { subscribe: vi.fn((cb) => (instance._onToggleCollapse = cb)) },
            onNavigateToLocation: { subscribe: vi.fn((cb) => (instance._onNavigateToLocation = cb)) },
            onApplyInlineFix: { subscribe: vi.fn((cb) => (instance._onApplyInlineFix = cb)) },
            setInlineFixOutdatedWarning: vi.fn(),
            hideAllCommentMenus: vi.fn(),
        };
        return {
            instance,
            location: { nativeElement: document.createElement('div') },
            setInput: vi.fn(),
            destroy: vi.fn(),
        } as any;
    };

    const createViewContainerRefMock = () => {
        const createComponent = vi.fn((componentType: any) => {
            if (componentType === ReviewCommentDraftWidgetComponent) {
                return createDraftRef();
            }
            if (componentType === ReviewCommentThreadWidgetComponent) {
                return createThreadRef();
            }
            throw new Error('Unexpected component');
        });
        return { createComponent };
    };

    const createConfig = (overrides: Partial<ReviewCommentWidgetManagerConfig> = {}): ReviewCommentWidgetManagerConfig => ({
        hoverButtonClass: 'hover',
        shouldShowHoverButton: () => true,
        canSubmit: () => true,
        getDraftFileName: () => 'file.java',
        getDraftContext: () => ({ targetType: CommentThreadLocationType.TEMPLATE_REPO, filePath: 'file.java' }),
        getThreads: () => [],
        filterThread: () => true,
        getThreadLine: (thread) => (thread.lineNumber ?? 1) - 1,
        onAdd: vi.fn(),
        showLocationWarning: () => false,
        showFeedbackAction: () => false,
        ...overrides,
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should update hover button based on config', () => {
        const editor = createEditorMock();
        const vcRef = createViewContainerRefMock();
        const config = createConfig();
        const manager = new ReviewCommentWidgetManager(editor as any, vcRef as any, config);

        manager.updateHoverButton();
        expect(editor.setLineDecorationsHoverButton).toHaveBeenCalled();

        const configHidden = createConfig({ shouldShowHoverButton: () => false });
        const managerHidden = new ReviewCommentWidgetManager(editor as any, vcRef as any, configHidden);
        managerHidden.updateHoverButton();
        expect(editor.clearLineDecorationsHoverButton).toHaveBeenCalled();
    });

    it('should add and remove draft widgets incrementally', () => {
        const editor = createEditorMock();
        const vcRef = createViewContainerRefMock();
        const onAdd = vi.fn();
        const config = createConfig({ onAdd });
        const manager = new ReviewCommentWidgetManager(editor as any, vcRef as any, config);

        manager.updateHoverButton();
        const addCallback = editor.setLineDecorationsHoverButton.mock.calls[0][1];
        addCallback(5);

        expect(editor.addLineWidget).toHaveBeenCalledWith(5, expect.stringContaining('review-comment-'), expect.any(HTMLElement));
        expect(onAdd).toHaveBeenCalledWith({ lineNumber: 5, fileName: 'file.java' });

        const draftRef = vcRef.createComponent.mock.results[0].value;
        draftRef.instance._onCancel();

        expect(editor.disposeWidgetsByPrefix).toHaveBeenCalledWith(expect.stringContaining('review-comment-'));
        expect(draftRef.destroy).toHaveBeenCalled();
    });

    it('should not create duplicate draft widgets when adding the same line twice', () => {
        const editor = createEditorMock();
        const vcRef = createViewContainerRefMock();
        const config = createConfig();
        const manager = new ReviewCommentWidgetManager(editor as any, vcRef as any, config);

        manager.updateHoverButton();
        const addCallback = editor.setLineDecorationsHoverButton.mock.calls[0][1];
        addCallback(5);
        addCallback(5);

        expect(vcRef.createComponent).toHaveBeenCalledOnce();
        expect(editor.addLineWidget).toHaveBeenCalledTimes(2);
        expect(editor.disposeWidgetsByPrefix).toHaveBeenCalledWith(expect.stringContaining('review-comment-file.java::4::'));
    });

    it('should add and remove thread widgets incrementally', () => {
        const editor = createEditorMock();
        const vcRef = createViewContainerRefMock();
        const threads: CommentThread[] = [{ id: 1, lineNumber: 2, resolved: false } as any];
        const config = createConfig({ getThreads: () => threads });
        const manager = new ReviewCommentWidgetManager(editor as any, vcRef as any, config);

        manager.renderWidgets();
        expect(editor.addLineWidget).toHaveBeenCalledWith(2, 'review-comment-thread-1', expect.any(HTMLElement));

        const threadRef = vcRef.createComponent.mock.results.find((r: any) => r.value.instance.onToggleCollapse)?.value;
        config.getThreads = () => [];
        manager.renderWidgets();

        expect(editor.disposeWidgetsByPrefix).toHaveBeenCalledWith('review-comment-thread-1');
        expect(threadRef.destroy).toHaveBeenCalled();
    });

    it('should submit draft and remove it', () => {
        const editor = createEditorMock();
        const vcRef = createViewContainerRefMock();
        const config = createConfig();
        const manager = new ReviewCommentWidgetManager(editor as any, vcRef as any, config);

        manager.updateHoverButton();
        const addCallback = editor.setLineDecorationsHoverButton.mock.calls[0][1];
        addCallback(3);

        const draftRef = vcRef.createComponent.mock.results[0].value;
        draftRef.instance._onSubmitted();

        expect(editor.disposeWidgetsByPrefix).toHaveBeenCalledWith(expect.stringContaining('review-comment-'));
    });

    it('should render draft widgets from stored lines', () => {
        const editor = createEditorMock();
        const vcRef = createViewContainerRefMock();
        const config = createConfig();
        const manager = new ReviewCommentWidgetManager(editor as any, vcRef as any, config);

        manager.updateHoverButton();
        const addCallback = editor.setLineDecorationsHoverButton.mock.calls[0][1];
        addCallback(4);

        // simulate fresh render without existing draft refs
        (manager as any).draftWidgetRefs.clear();
        manager.renderWidgets();

        expect(vcRef.createComponent).toHaveBeenCalledWith(ReviewCommentDraftWidgetComponent);
        expect(editor.addLineWidget).toHaveBeenCalledWith(4, expect.stringContaining('review-comment-'), expect.any(HTMLElement));
    });

    it('should update draft submit state and clear draft widgets', () => {
        const editor = createEditorMock();
        const vcRef = createViewContainerRefMock();
        const config = createConfig();
        const manager = new ReviewCommentWidgetManager(editor as any, vcRef as any, config);

        manager.updateHoverButton();
        const addCallback = editor.setLineDecorationsHoverButton.mock.calls[0][1];
        addCallback(6);

        const draftRef = vcRef.createComponent.mock.results[0].value;
        config.canSubmit = () => false;
        manager.updateDraftInputs();
        manager.clearDrafts();
        manager.renderWidgets();

        expect(draftRef.setInput).toHaveBeenCalledWith('canSubmit', false);
        expect(draftRef.submittedSubscription.unsubscribe).toHaveBeenCalled();
        expect(draftRef.cancelSubscription.unsubscribe).toHaveBeenCalled();
        expect(editor.disposeWidgetsByPrefix).toHaveBeenCalledWith('review-comment-file.java::5::');
        expect(draftRef.destroy).toHaveBeenCalledOnce();
        expect(vcRef.createComponent).toHaveBeenCalledOnce();
    });

    it('should dispose draft and thread widgets on disposeAll', () => {
        const editor = createEditorMock();
        const vcRef = createViewContainerRefMock();
        const threads: CommentThread[] = [{ id: 2, lineNumber: 1, resolved: false } as any];
        const config = createConfig({ getThreads: () => threads });
        const manager = new ReviewCommentWidgetManager(editor as any, vcRef as any, config);

        manager.updateHoverButton();
        const addCallback = editor.setLineDecorationsHoverButton.mock.calls[0][1];
        addCallback(2);
        manager.renderWidgets();

        const draftRef = vcRef.createComponent.mock.results.find((r: any) => r.value.instance.onSubmitted)?.value;
        const threadRef = vcRef.createComponent.mock.results.find((r: any) => r.value.instance.onToggleCollapse)?.value;

        manager.disposeAll();

        expect(editor.disposeWidgetsByPrefix).toHaveBeenCalledWith('review-comment-');
        expect(draftRef.destroy).toHaveBeenCalled();
        expect(threadRef.destroy).toHaveBeenCalled();
    });

    it('should update thread inputs when widgets exist', () => {
        const editor = createEditorMock();
        const vcRef = createViewContainerRefMock();
        const threads: CommentThread[] = [{ id: 3, lineNumber: 1, resolved: false } as any];
        const config = createConfig({ getThreads: () => threads });
        const manager = new ReviewCommentWidgetManager(editor as any, vcRef as any, config);

        manager.renderWidgets();
        const updated = manager.tryUpdateThreadInputs(threads);
        expect(updated).toBe(true);
    });

    it('should return false when thread widget is missing', () => {
        const editor = createEditorMock();
        const vcRef = createViewContainerRefMock();
        const config = createConfig();
        const manager = new ReviewCommentWidgetManager(editor as any, vcRef as any, config);

        const updated = manager.tryUpdateThreadInputs([{ id: 99 } as any]);
        expect(updated).toBe(false);
    });

    it('should hide open thread menus when the editor scrolls', () => {
        const editor = createEditorMock();
        const vcRef = createViewContainerRefMock();
        const threads: CommentThread[] = [{ id: 11, lineNumber: 3, resolved: false } as any];
        const config = createConfig({ getThreads: () => threads });
        const manager = new ReviewCommentWidgetManager(editor as any, vcRef as any, config);

        manager.renderWidgets();
        const threadRef = vcRef.createComponent.mock.results.find((r: any) => r.value.instance.onToggleCollapse)?.value;
        editor.triggerScroll();

        expect(threadRef.instance.hideAllCommentMenus).toHaveBeenCalledTimes(1);
    });

    it('should apply inline fix when expected code matches editor content', () => {
        const editor = createEditorMock();
        editor.setModelLines(['class Example {', '    public class QuickSort {', '}']);
        const vcRef = createViewContainerRefMock();
        const threads: CommentThread[] = [{ id: 15, lineNumber: 2, resolved: false } as any];
        const onApplyInlineFix = vi.fn();
        const config = createConfig({ getThreads: () => threads, onApplyInlineFix });
        const manager = new ReviewCommentWidgetManager(editor as any, vcRef as any, config);

        manager.renderWidgets();
        const threadRef = vcRef.createComponent.mock.results.find((r: any) => r.value.instance.onApplyInlineFix)?.value;
        threadRef.instance._onApplyInlineFix({
            startLine: 2,
            endLine: 2,
            expectedCode: '    public class QuickSort {',
            replacementCode: '    public class BubbleSort {',
            applied: false,
        });

        expect(editor.getActiveEditor().executeEdits).toHaveBeenCalledWith('ReviewCommentWidgetManager::applyInlineFix', [
            {
                range: {
                    startLineNumber: 2,
                    startColumn: 1,
                    endLineNumber: 2,
                    endColumn: 29,
                },
                text: '    public class BubbleSort {',
                forceMoveMarkers: true,
            },
        ]);
        expect(threadRef.instance.setInlineFixOutdatedWarning).toHaveBeenCalledWith(false);
        expect(onApplyInlineFix).toHaveBeenCalledWith({
            thread: threads[0],
            inlineFix: {
                startLine: 2,
                endLine: 2,
                expectedCode: '    public class QuickSort {',
                replacementCode: '    public class BubbleSort {',
                applied: false,
            },
        });
    });

    it('should not apply inline fix when expected code does not match editor content', () => {
        const editor = createEditorMock();
        editor.setModelLines(['class Example {', '    public class QuickSort {', '}']);
        const vcRef = createViewContainerRefMock();
        const threads: CommentThread[] = [{ id: 16, lineNumber: 2, resolved: false } as any];
        const onApplyInlineFix = vi.fn();
        const config = createConfig({ getThreads: () => threads, onApplyInlineFix });
        const manager = new ReviewCommentWidgetManager(editor as any, vcRef as any, config);

        manager.renderWidgets();
        const threadRef = vcRef.createComponent.mock.results.find((r: any) => r.value.instance.onApplyInlineFix)?.value;
        threadRef.instance._onApplyInlineFix({
            startLine: 2,
            endLine: 2,
            expectedCode: '    public class BubbleSort {',
            replacementCode: '    public class BubbleSort {',
            applied: false,
        });

        expect(editor.getActiveEditor().executeEdits).not.toHaveBeenCalled();
        expect(threadRef.instance.setInlineFixOutdatedWarning).toHaveBeenCalledWith(true);
        expect(onApplyInlineFix).not.toHaveBeenCalled();
    });

    it('should not persist inline fix when the editor model is unavailable', () => {
        const editor = createEditorMock();
        editor.getModel.mockReturnValue(undefined);
        const vcRef = createViewContainerRefMock();
        const threads: CommentThread[] = [{ id: 17, lineNumber: 2, resolved: false } as any];
        const onApplyInlineFix = vi.fn();
        const config = createConfig({ getThreads: () => threads, onApplyInlineFix });
        const manager = new ReviewCommentWidgetManager(editor as any, vcRef as any, config);

        manager.renderWidgets();
        const threadRef = vcRef.createComponent.mock.results.find((r: any) => r.value.instance.onApplyInlineFix)?.value;
        threadRef.instance._onApplyInlineFix({
            startLine: 2,
            endLine: 2,
            expectedCode: 'foo',
            replacementCode: 'bar',
            applied: false,
        });

        expect(threadRef.instance.setInlineFixOutdatedWarning).toHaveBeenCalledWith(false);
        expect(editor.getActiveEditor().executeEdits).not.toHaveBeenCalled();
        expect(onApplyInlineFix).not.toHaveBeenCalled();
    });

    it('should not persist inline fix when the target range is invalid', () => {
        const editor = createEditorMock();
        editor.setModelLines(['class Example {']);
        const vcRef = createViewContainerRefMock();
        const threads: CommentThread[] = [{ id: 18, lineNumber: 1, resolved: false } as any];
        const onApplyInlineFix = vi.fn();
        const config = createConfig({ getThreads: () => threads, onApplyInlineFix });
        const manager = new ReviewCommentWidgetManager(editor as any, vcRef as any, config);

        manager.renderWidgets();
        const threadRef = vcRef.createComponent.mock.results.find((r: any) => r.value.instance.onApplyInlineFix)?.value;
        threadRef.instance._onApplyInlineFix({
            startLine: 2,
            endLine: 2,
            expectedCode: 'foo',
            replacementCode: 'bar',
            applied: false,
        });

        expect(threadRef.instance.setInlineFixOutdatedWarning).toHaveBeenCalledWith(false);
        expect(editor.getActiveEditor().executeEdits).not.toHaveBeenCalled();
        expect(onApplyInlineFix).not.toHaveBeenCalled();
    });
});
