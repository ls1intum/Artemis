import { beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { CodeEditorGridComponent } from 'app/programming/shared/code-editor/layout/code-editor-grid/code-editor-grid.component';
import { InteractableEvent } from 'app/programming/manage/code-editor/file-browser/code-editor-file-browser.component';
import { CollapsableCodeEditorElement } from 'app/programming/manage/code-editor/container/code-editor-container.component';

const fileBrowserWindowName = 'FileBrowser';
const instructionsWindowName = 'Instructions';
const buildOutputWindowName = 'BuildOutput';

describe('CodeEditorGridComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: CodeEditorGridComponent;
    let fixture: ComponentFixture<CodeEditorGridComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({})
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

            const blur = () => {};
            const pointerEvent: PointerEvent = { type: 'click', target: { blur } as unknown as HTMLElement } as unknown as PointerEvent;

            const windowCollapseEvent: InteractableEvent = { event: pointerEvent, horizontal: true };

            expectWindowToBeCollapsed(windowName, false);

            comp.toggleCollapse(windowCollapseEvent, collapsableElement);

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

    describe('Editor / build-output divider coupling', () => {
        it('shrinks the build output as the editor area grows, and vice versa, so the divider transfers space', () => {
            fixture.detectChanges();
            const wrapper = fixture.nativeElement.querySelector('.editor-wrapper') as HTMLElement;
            const main = wrapper.querySelector('.editor-main') as HTMLElement;
            const bottom = wrapper.querySelector('.editor-bottom') as HTMLElement;
            // Pin the editor's top so availableVerticalSpace is deterministic.
            Object.defineProperty(main, 'getBoundingClientRect', { value: () => ({ top: 100 }) as DOMRect, configurable: true });
            const available = window.innerHeight - 100 - 40; // matches VERTICAL_BUFFER_PX

            // Dragging the editor divider grows the editor area; the build output shrinks to fill the rest.
            (comp as any).onVerticalPanelResize('main', { width: 0, height: 200 });
            expect(bottom.style.height).toBe(`${Math.max(comp.resizableMinHeightBottom, Math.min(600, available - 200))}px`);

            // Dragging the build-output divider grows it; the editor area shrinks in step.
            (comp as any).onVerticalPanelResize('bottom', { width: 0, height: 250 });
            expect(main.style.height).toBe(`${Math.max(comp.resizableMinHeightMain, Math.min(1200, available - 250))}px`);
        });
    });
});
