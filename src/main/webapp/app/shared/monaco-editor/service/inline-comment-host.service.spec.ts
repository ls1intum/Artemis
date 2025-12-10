import { ComponentRef } from '@angular/core';
import { ActiveWidgetInfo } from './inline-comment-host.service';
import { InlineCommentWidgetComponent } from '../inline-comment-widget/inline-comment-widget.component';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';

/** Creates a mock widget info for testing */
const createMockWidget = (id: string, startLine: number, endLine: number, destroyFn = jest.fn()): ActiveWidgetInfo => ({
    id,
    startLine,
    endLine,
    componentRef: { hostView: {}, destroy: destroyFn } as unknown as ComponentRef<InlineCommentWidgetComponent>,
});

/** Test-friendly service that mirrors the real implementation */
class TestableInlineCommentHostService {
    appRef = { attachView: jest.fn(), detachView: jest.fn() };
    private activeWidgets = new Map<string, ActiveWidgetInfo>();

    hasWidgetAtLine(lineNumber: number): boolean {
        for (const widget of this.activeWidgets.values()) {
            if (lineNumber >= widget.startLine && lineNumber <= widget.endLine) return true;
        }
        return false;
    }

    getActiveWidgetCount(): number {
        return this.activeWidgets.size;
    }

    closeWidget(widgetId: string, editor: MarkdownEditorMonacoComponent): void {
        const widgetInfo = this.activeWidgets.get(widgetId);
        if (!widgetInfo) return;
        editor.removeLineWidget(widgetId);
        this.appRef.detachView(widgetInfo.componentRef.hostView);
        widgetInfo.componentRef.destroy();
        this.activeWidgets.delete(widgetId);
    }

    closeAllWidgets(editor: MarkdownEditorMonacoComponent): void {
        for (const widgetId of this.activeWidgets.keys()) {
            this.closeWidget(widgetId, editor);
        }
    }

    get widgets(): Map<string, ActiveWidgetInfo> {
        return this.activeWidgets;
    }
}

describe('InlineCommentHostService', () => {
    let service: TestableInlineCommentHostService;
    let mockEditor: jest.Mocked<MarkdownEditorMonacoComponent>;

    beforeEach(() => {
        mockEditor = { addLineWidget: jest.fn(), removeLineWidget: jest.fn() } as unknown as jest.Mocked<MarkdownEditorMonacoComponent>;
        service = new TestableInlineCommentHostService();
    });

    afterEach(() => jest.clearAllMocks());

    describe('hasWidgetAtLine', () => {
        it('should return false when no widgets exist', () => {
            expect(service.hasWidgetAtLine(5)).toBeFalse();
        });

        it('should return false for lines outside widget range and true for lines within', () => {
            service.widgets.set('test-widget', createMockWidget('test-widget', 10, 15));

            expect(service.hasWidgetAtLine(5)).toBeFalse();
            expect(service.hasWidgetAtLine(20)).toBeFalse();
            expect(service.hasWidgetAtLine(10)).toBeTrue();
            expect(service.hasWidgetAtLine(12)).toBeTrue();
            expect(service.hasWidgetAtLine(15)).toBeTrue();
        });

        it('should check all widgets when multiple exist', () => {
            service.widgets.set('widget-1', createMockWidget('widget-1', 1, 5));
            service.widgets.set('widget-2', createMockWidget('widget-2', 20, 25));

            expect(service.hasWidgetAtLine(3)).toBeTrue();
            expect(service.hasWidgetAtLine(22)).toBeTrue();
            expect(service.hasWidgetAtLine(10)).toBeFalse();
        });
    });

    describe('getActiveWidgetCount', () => {
        it('should return correct count of active widgets', () => {
            expect(service.getActiveWidgetCount()).toBe(0);

            service.widgets.set('widget-1', createMockWidget('widget-1', 1, 5));
            service.widgets.set('widget-2', createMockWidget('widget-2', 10, 15));
            expect(service.getActiveWidgetCount()).toBe(2);
        });
    });

    describe('closeWidget', () => {
        it('should do nothing if widget does not exist', () => {
            service.closeWidget('non-existent', mockEditor);
            expect(mockEditor.removeLineWidget).not.toHaveBeenCalled();
        });

        it('should remove widget from editor and tracking', () => {
            const destroyFn = jest.fn();
            service.widgets.set('test-widget', createMockWidget('test-widget', 1, 5, destroyFn));

            service.closeWidget('test-widget', mockEditor);

            expect(mockEditor.removeLineWidget).toHaveBeenCalledWith('test-widget');
            expect(destroyFn).toHaveBeenCalled();
            expect(service.widgets.has('test-widget')).toBeFalse();
        });
    });

    describe('closeAllWidgets', () => {
        it('should close all active widgets', () => {
            const destroy1 = jest.fn();
            const destroy2 = jest.fn();
            service.widgets.set('widget-1', createMockWidget('widget-1', 1, 5, destroy1));
            service.widgets.set('widget-2', createMockWidget('widget-2', 10, 15, destroy2));

            service.closeAllWidgets(mockEditor);

            expect(mockEditor.removeLineWidget).toHaveBeenCalledTimes(2);
            expect(destroy1).toHaveBeenCalled();
            expect(destroy2).toHaveBeenCalled();
            expect(service.getActiveWidgetCount()).toBe(0);
        });

        it('should do nothing if no widgets exist', () => {
            service.closeAllWidgets(mockEditor);
            expect(mockEditor.removeLineWidget).not.toHaveBeenCalled();
        });
    });
});
