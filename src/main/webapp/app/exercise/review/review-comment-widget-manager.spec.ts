import { ReviewCommentWidgetManager, ReviewCommentWidgetManagerConfig } from 'app/exercise/review/review-comment-widget-manager';
import { ReviewCommentDraftWidgetComponent } from 'app/exercise/review/review-comment-draft-widget/review-comment-draft-widget.component';
import { ReviewCommentThreadWidgetComponent } from 'app/exercise/review/review-comment-thread-widget/review-comment-thread-widget.component';
import { CommentThread } from 'app/exercise/shared/entities/review/comment-thread.model';

describe('ReviewCommentWidgetManager', () => {
    const createEditorMock = () => ({
        setLineDecorationsHoverButton: jest.fn(),
        clearLineDecorationsHoverButton: jest.fn(),
        addLineWidget: jest.fn(),
        disposeWidgetsByPrefix: jest.fn(),
    });

    const createDraftRef = () => {
        const instance: any = {
            onSubmit: { subscribe: jest.fn((cb) => (instance._onSubmit = cb)) },
            onCancel: { subscribe: jest.fn((cb) => (instance._onCancel = cb)) },
        };
        return {
            instance,
            location: { nativeElement: document.createElement('div') },
            setInput: jest.fn(),
            destroy: jest.fn(),
        } as any;
    };

    const createThreadRef = () => {
        const instance: any = {
            onDelete: { subscribe: jest.fn((cb) => (instance._onDelete = cb)) },
            onReply: { subscribe: jest.fn((cb) => (instance._onReply = cb)) },
            onUpdate: { subscribe: jest.fn((cb) => (instance._onUpdate = cb)) },
            onToggleResolved: { subscribe: jest.fn((cb) => (instance._onToggleResolved = cb)) },
            onToggleCollapse: { subscribe: jest.fn((cb) => (instance._onToggleCollapse = cb)) },
        };
        return {
            instance,
            location: { nativeElement: document.createElement('div') },
            setInput: jest.fn(),
            destroy: jest.fn(),
        } as any;
    };

    const createViewContainerRefMock = () => {
        const createComponent = jest.fn((componentType: any) => {
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
        getThreads: () => [],
        filterThread: () => true,
        getThreadLine: (thread) => (thread.lineNumber ?? 1) - 1,
        onAdd: jest.fn(),
        onSubmit: jest.fn(),
        onDelete: jest.fn(),
        onReply: jest.fn(),
        onUpdate: jest.fn(),
        onToggleResolved: jest.fn(),
        showLocationWarning: () => false,
        ...overrides,
    });

    afterEach(() => {
        jest.restoreAllMocks();
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
        const onAdd = jest.fn();
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
        expect(editor.disposeWidgetsByPrefix).toHaveBeenCalledWith(expect.stringContaining('review-comment-file.java-4'));
    });

    it('should add and remove thread widgets incrementally', () => {
        const editor = createEditorMock();
        const vcRef = createViewContainerRefMock();
        const threads: CommentThread[] = [{ id: 1, lineNumber: 2, resolved: false } as any];
        const config = createConfig({ getThreads: () => threads });
        const manager = new ReviewCommentWidgetManager(editor as any, vcRef as any, config);

        manager.renderWidgets();
        expect(editor.addLineWidget).toHaveBeenCalledWith(2, 'review-comment-thread-1', expect.any(HTMLElement));

        const threadRef = vcRef.createComponent.mock.results.find((r: any) => r.value.instance.onToggleResolved)?.value;
        config.getThreads = () => [];
        manager.renderWidgets();

        expect(editor.disposeWidgetsByPrefix).toHaveBeenCalledWith('review-comment-thread-1');
        expect(threadRef.destroy).toHaveBeenCalled();
    });

    it('should submit draft and remove it', () => {
        const editor = createEditorMock();
        const vcRef = createViewContainerRefMock();
        const onSubmit = jest.fn();
        const config = createConfig({ onSubmit });
        const manager = new ReviewCommentWidgetManager(editor as any, vcRef as any, config);

        manager.updateHoverButton();
        const addCallback = editor.setLineDecorationsHoverButton.mock.calls[0][1];
        addCallback(3);

        const draftRef = vcRef.createComponent.mock.results[0].value;
        draftRef.instance._onSubmit('text');

        expect(onSubmit).toHaveBeenCalledWith({ lineNumber: 3, fileName: 'file.java', initialComment: { contentType: 'USER', text: 'text' } });
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

        const draftRef = vcRef.createComponent.mock.results.find((r: any) => r.value.instance.onSubmit)?.value;
        const threadRef = vcRef.createComponent.mock.results.find((r: any) => r.value.instance.onToggleResolved)?.value;

        manager.disposeAll();

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
        const updated = manager.updateThreadInputs(threads);
        expect(updated).toBeTrue();
    });

    it('should return false when thread widget is missing', () => {
        const editor = createEditorMock();
        const vcRef = createViewContainerRefMock();
        const config = createConfig();
        const manager = new ReviewCommentWidgetManager(editor as any, vcRef as any, config);

        const updated = manager.updateThreadInputs([{ id: 99 } as any]);
        expect(updated).toBeFalse();
    });
});
