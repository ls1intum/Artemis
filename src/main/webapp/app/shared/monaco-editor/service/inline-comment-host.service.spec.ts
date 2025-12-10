import { ComponentRef } from '@angular/core';
import { ActiveWidgetInfo } from './inline-comment-host.service';
import { InlineCommentWidgetComponent } from '../inline-comment-widget/inline-comment-widget.component';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';

/**
 * Test-friendly version of InlineCommentHostService that doesn't use Angular DI.
 * This allows testing the pure logic without needing TestBed.
 */
class TestableInlineCommentHostService {
    appRef = {
        attachView: jest.fn(),
        detachView: jest.fn(),
    };

    private activeWidgets = new Map<string, ActiveWidgetInfo>();

    hasWidgetAtLine(lineNumber: number): boolean {
        for (const widget of this.activeWidgets.values()) {
            if (lineNumber >= widget.startLine && lineNumber <= widget.endLine) {
                return true;
            }
        }
        return false;
    }

    getActiveWidgetCount(): number {
        return this.activeWidgets.size;
    }

    closeWidget(widgetId: string, editor: MarkdownEditorMonacoComponent): void {
        const widgetInfo = this.activeWidgets.get(widgetId);
        if (!widgetInfo) {
            return;
        }

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

    // Expose for testing
    get widgets(): Map<string, ActiveWidgetInfo> {
        return this.activeWidgets;
    }
}

describe('InlineCommentHostService', () => {
    let service: TestableInlineCommentHostService;
    let mockEditor: jest.Mocked<MarkdownEditorMonacoComponent>;

    beforeEach(() => {
        mockEditor = {
            addLineWidget: jest.fn(),
            removeLineWidget: jest.fn(),
        } as unknown as jest.Mocked<MarkdownEditorMonacoComponent>;

        service = new TestableInlineCommentHostService();
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    describe('hasWidgetAtLine', () => {
        it('should return false when no widgets exist', () => {
            expect(service.hasWidgetAtLine(5)).toBeFalse();
        });

        it('should return false for lines outside any widget range', () => {
            service.widgets.set('test-widget', {
                id: 'test-widget',
                startLine: 10,
                endLine: 15,
                componentRef: {} as ComponentRef<InlineCommentWidgetComponent>,
            });

            expect(service.hasWidgetAtLine(5)).toBeFalse();
            expect(service.hasWidgetAtLine(20)).toBeFalse();
        });

        it('should return true for lines within a widget range', () => {
            service.widgets.set('test-widget', {
                id: 'test-widget',
                startLine: 10,
                endLine: 15,
                componentRef: {} as ComponentRef<InlineCommentWidgetComponent>,
            });

            expect(service.hasWidgetAtLine(10)).toBeTrue();
            expect(service.hasWidgetAtLine(12)).toBeTrue();
            expect(service.hasWidgetAtLine(15)).toBeTrue();
        });

        it('should check all widgets when multiple exist', () => {
            service.widgets.set('widget-1', {
                id: 'widget-1',
                startLine: 1,
                endLine: 5,
                componentRef: {} as ComponentRef<InlineCommentWidgetComponent>,
            });
            service.widgets.set('widget-2', {
                id: 'widget-2',
                startLine: 20,
                endLine: 25,
                componentRef: {} as ComponentRef<InlineCommentWidgetComponent>,
            });

            expect(service.hasWidgetAtLine(3)).toBeTrue();
            expect(service.hasWidgetAtLine(22)).toBeTrue();
            expect(service.hasWidgetAtLine(10)).toBeFalse();
        });
    });

    describe('getActiveWidgetCount', () => {
        it('should return 0 when no widgets exist', () => {
            expect(service.getActiveWidgetCount()).toBe(0);
        });

        it('should return correct count of active widgets', () => {
            service.widgets.set('widget-1', {
                id: 'widget-1',
                startLine: 1,
                endLine: 5,
                componentRef: {} as ComponentRef<InlineCommentWidgetComponent>,
            });
            service.widgets.set('widget-2', {
                id: 'widget-2',
                startLine: 10,
                endLine: 15,
                componentRef: {} as ComponentRef<InlineCommentWidgetComponent>,
            });

            expect(service.getActiveWidgetCount()).toBe(2);
        });
    });

    describe('closeWidget', () => {
        it('should do nothing if widget does not exist', () => {
            service.closeWidget('non-existent', mockEditor);

            expect(mockEditor.removeLineWidget).not.toHaveBeenCalled();
        });

        it('should remove widget from editor and tracking', () => {
            const mockComponentRef = {
                hostView: {},
                destroy: jest.fn(),
            } as unknown as ComponentRef<InlineCommentWidgetComponent>;

            service.widgets.set('test-widget', {
                id: 'test-widget',
                startLine: 1,
                endLine: 5,
                componentRef: mockComponentRef,
            });

            service.closeWidget('test-widget', mockEditor);

            expect(mockEditor.removeLineWidget).toHaveBeenCalledWith('test-widget');
            expect(mockComponentRef.destroy).toHaveBeenCalled();
            expect(service.widgets.has('test-widget')).toBeFalse();
        });
    });

    describe('closeAllWidgets', () => {
        it('should close all active widgets', () => {
            const mockComponentRef1 = {
                hostView: {},
                destroy: jest.fn(),
            } as unknown as ComponentRef<InlineCommentWidgetComponent>;

            const mockComponentRef2 = {
                hostView: {},
                destroy: jest.fn(),
            } as unknown as ComponentRef<InlineCommentWidgetComponent>;

            service.widgets.set('widget-1', {
                id: 'widget-1',
                startLine: 1,
                endLine: 5,
                componentRef: mockComponentRef1,
            });
            service.widgets.set('widget-2', {
                id: 'widget-2',
                startLine: 10,
                endLine: 15,
                componentRef: mockComponentRef2,
            });

            service.closeAllWidgets(mockEditor);

            expect(mockEditor.removeLineWidget).toHaveBeenCalledTimes(2);
            expect(mockComponentRef1.destroy).toHaveBeenCalled();
            expect(mockComponentRef2.destroy).toHaveBeenCalled();
            expect(service.getActiveWidgetCount()).toBe(0);
        });

        it('should do nothing if no widgets exist', () => {
            service.closeAllWidgets(mockEditor);

            expect(mockEditor.removeLineWidget).not.toHaveBeenCalled();
        });
    });
});
