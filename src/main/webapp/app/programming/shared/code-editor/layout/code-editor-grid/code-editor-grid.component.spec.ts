import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { CodeEditorGridComponent } from 'app/programming/shared/code-editor/layout/code-editor-grid/code-editor-grid.component';
import { InteractableEvent } from 'app/programming/manage/code-editor/file-browser/code-editor-file-browser.component';
import { CollapsableCodeEditorElement } from 'app/programming/manage/code-editor/container/code-editor-container.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ResizeType } from 'app/programming/shared/code-editor/model/code-editor.model';

const fileBrowserWindowName = 'FileBrowser';
const instructionsWindowName = 'Instructions';
const buildOutputWindowName = 'BuildOutput';

describe('CodeEditorGridComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: CodeEditorGridComponent;
    let fixture: ComponentFixture<CodeEditorGridComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [{ provide: TranslateService, useClass: MockTranslateService }] })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CodeEditorGridComponent);
                comp = fixture.componentInstance;
            });
    });

    describe('Hide draggable icons', () => {
        it('should hide draggable icon for file browser', () => {
            executeHideDraggableIconTestForWindow(fileBrowserWindowName, CollapsableCodeEditorElement.FileBrowser);
        });

        // right panel = Instruction / Problem Statement
        it('should hide draggable icon for right panel', () => {
            executeHideDraggableIconTestForWindow(instructionsWindowName, CollapsableCodeEditorElement.Instructions);
        });

        it('should hide draggable icon for build output', () => {
            executeHideDraggableIconTestForWindow(buildOutputWindowName, CollapsableCodeEditorElement.BuildOutput);
        });

        const executeHideDraggableIconTestForWindow = (windowName: string, collapsableElement: CollapsableCodeEditorElement) => {
            fixture.detectChanges();
            let draggableIconForWindow = getDebugElement(windowName);

            expect(draggableIconForWindow).not.toBeNull();

            const blur = vi.fn();
            const pointerEvent: PointerEvent = { type: 'click', target: { blur } as unknown as HTMLElement } as unknown as PointerEvent;

            const windowCollapseEvent: InteractableEvent = { event: pointerEvent, horizontal: true };

            expectWindowToBeCollapsed(windowName, false);

            comp.toggleCollapse(windowCollapseEvent, collapsableElement);

            expect(blur).not.toHaveBeenCalled();

            fixture.changeDetectorRef.detectChanges();

            draggableIconForWindow = getDebugElement(windowName);
            expectWindowToBeCollapsed(windowName, true);
            expect(draggableIconForWindow).toBeNull();
        };

        const getDebugElement = (windowName: string) => {
            return fixture.debugElement.query(By.css('#draggableIconFor' + windowName));
        };

        const expectAllWindowsToNotBeCollapsed = () => {
            expect(comp.fileBrowserIsCollapsed()).toBe(false);
            expect(comp.rightPanelIsCollapsed()).toBe(false);
            expect(comp.buildOutputIsCollapsed()).toBe(false);
        };

        const expectWindowToBeCollapsed = (windowName: string, collapsed: boolean) => {
            switch (windowName) {
                case fileBrowserWindowName: {
                    if (collapsed) {
                        expect(comp.fileBrowserIsCollapsed()).toBe(true);
                        expect(comp.rightPanelIsCollapsed()).toBe(false);
                        expect(comp.buildOutputIsCollapsed()).toBe(false);
                    } else {
                        expectAllWindowsToNotBeCollapsed();
                    }
                    break;
                }
                case instructionsWindowName: {
                    if (collapsed) {
                        expect(comp.fileBrowserIsCollapsed()).toBe(false);
                        expect(comp.rightPanelIsCollapsed()).toBe(true);
                        expect(comp.buildOutputIsCollapsed()).toBe(false);
                    } else {
                        expectAllWindowsToNotBeCollapsed();
                    }
                    break;
                }
                case buildOutputWindowName: {
                    if (collapsed) {
                        expect(comp.fileBrowserIsCollapsed()).toBe(false);
                        expect(comp.rightPanelIsCollapsed()).toBe(false);
                        expect(comp.buildOutputIsCollapsed()).toBe(true);
                    } else {
                        expectAllWindowsToNotBeCollapsed();
                    }
                    break;
                }
            }
        };
    });

    it('expands the bottom panel idempotently', () => {
        fixture.detectChanges();
        const bottomPanel = fixture.nativeElement.querySelector('.editor-bottom') as HTMLElement;
        bottomPanel.classList.add('collapsed--vertical');
        comp.buildOutputIsCollapsed.set(true);

        comp.expandBottomPanel();

        expect(comp.buildOutputIsCollapsed()).toBe(false);
        expect(bottomPanel.classList.contains('collapsed--vertical')).toBe(false);

        const removeClass = vi.spyOn((comp as any).renderer, 'removeClass');
        comp.expandBottomPanel();
        expect(removeClass).not.toHaveBeenCalled();
    });

    it('provides keyboard alternatives for every resize handle', () => {
        fixture.detectChanges();
        const main = fixture.nativeElement.querySelector('.editor-main') as HTMLElement;
        const bottomPanel = fixture.nativeElement.querySelector('.editor-bottom') as HTMLElement;
        const left = fixture.nativeElement.querySelector('.editor-sidebar-left') as HTMLElement;
        const right = fixture.nativeElement.querySelector('.editor-sidebar-right') as HTMLElement;
        Object.defineProperty(main, 'offsetHeight', { configurable: true, value: 500 });
        Object.defineProperty(bottomPanel, 'offsetHeight', { configurable: true, value: 200 });
        Object.defineProperty(left, 'offsetWidth', { configurable: true, value: 310 });
        Object.defineProperty(right, 'offsetWidth', { configurable: true, value: 500 });
        (comp as any).maxConstraints.set({ heightMain: 800, heightBottom: 400, widthLeft: 600, widthRight: 700 });
        vi.spyOn(comp as any, 'recomputeMaxConstraints').mockImplementation(() => undefined);
        const resizeSpy = vi.spyOn(comp.onResize, 'emit');

        comp.onResizeHandleKeydown(new KeyboardEvent('keydown', { key: 'ArrowDown', cancelable: true }), ResizeType.MAIN_BOTTOM);
        comp.onResizeHandleKeydown(new KeyboardEvent('keydown', { key: 'ArrowUp', cancelable: true }), ResizeType.BOTTOM);
        comp.onResizeHandleKeydown(new KeyboardEvent('keydown', { key: 'ArrowRight', cancelable: true }), ResizeType.SIDEBAR_LEFT);
        comp.onResizeHandleKeydown(new KeyboardEvent('keydown', { key: 'ArrowLeft', cancelable: true }), ResizeType.SIDEBAR_RIGHT);

        expect(main.style.height).toBe('520px');
        expect(bottomPanel.style.height).toBe('220px');
        expect(left.style.width).toBe('330px');
        expect(right.style.width).toBe('520px');
        expect(resizeSpy).toHaveBeenCalledTimes(4);
    });

    it('exposes truthful focusable semantics for every resize handle', () => {
        fixture.detectChanges();

        const separators = ['#draggableIconForFileBrowser', '#draggableIconForInstructions', '#draggableIconForEditorMain'].map(
            (selector) => fixture.nativeElement.querySelector(selector) as HTMLElement,
        );
        const bottomHandle = fixture.nativeElement.querySelector('#draggableIconForBuildOutput') as HTMLElement;

        for (const handle of [...separators, bottomHandle]) {
            expect(handle.getAttribute('tabindex')).toBe('0');
            expect(handle.getAttribute('aria-valuemin')).not.toBeNull();
            expect(handle.getAttribute('aria-valuemax')).not.toBeNull();
            expect(handle.getAttribute('aria-valuenow')).not.toBeNull();
        }
        expect(separators.every((handle) => handle.getAttribute('role') === 'separator')).toBe(true);
        expect(bottomHandle.getAttribute('role')).toBe('slider');
    });
});
