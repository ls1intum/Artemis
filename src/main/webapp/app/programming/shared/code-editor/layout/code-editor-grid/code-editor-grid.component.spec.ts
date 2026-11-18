import { Mock, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { CodeEditorGridComponent } from 'app/programming/shared/code-editor/layout/code-editor-grid/code-editor-grid.component';
import { InteractableEvent } from 'app/programming/manage/code-editor/file-browser/code-editor-file-browser.component';
import { CollapsableCodeEditorElement } from 'app/programming/manage/code-editor/container/code-editor-container.component';

const fileBrowserWindowName = 'FileBrowser';
const instructionsWindowName = 'Instructions';
const buildOutputWindowName = 'BuildOutput';

const dividerTestIds = ['draggableIconForFileBrowser', 'draggableIconForInstructions', 'draggableIconForMain', 'draggableIconForBuildOutput'];

describe('CodeEditorGridComponent', () => {
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

    describe('Dividers', () => {
        const divider = (testId: string): HTMLElement | null => fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);

        const collapse = (collapsableElement: CollapsableCodeEditorElement) => {
            const event = { event: { type: 'click', target: { blur: () => {} } } as unknown as PointerEvent, horizontal: true } as InteractableEvent;
            comp.toggleCollapse(event, collapsableElement);
            fixture.changeDetectorRef.detectChanges();
        };

        it.each(dividerTestIds)('renders %s as a plain handle without a grip icon, so that the stylesheet alone sizes it', (testId) => {
            fixture.detectChanges();

            const handle = divider(testId);

            expect(handle).not.toBeNull();
            expect(handle!.tagName).toBe('DIV');
            expect(handle!.children).toHaveLength(0);
        });

        it('hands every divider to the resizable directive, which gives it the resize cursor of its direction', async () => {
            fixture.detectChanges();
            await fixture.whenStable();

            expect(divider('draggableIconForFileBrowser')!.style.cursor).toBe('col-resize');
            expect(divider('draggableIconForInstructions')!.style.cursor).toBe('col-resize');
            expect(divider('draggableIconForMain')!.style.cursor).toBe('row-resize');
            expect(divider('draggableIconForBuildOutput')!.style.cursor).toBe('row-resize');
        });

        it('keeps a spacer as wide as the divider in place of the divider of a collapsed file browser', () => {
            fixture.detectChanges();
            const sidebar = fixture.nativeElement.querySelector('.editor-sidebar-left') as HTMLElement;
            expect(sidebar.querySelector('.separator')).toBeNull();

            collapse(CollapsableCodeEditorElement.FileBrowser);

            expect(divider('draggableIconForFileBrowser')).toBeNull();
            expect(sidebar.querySelector('.separator')).not.toBeNull();
            expect(sidebar.classList.contains('collapsed--horizontal')).toBe(true);
        });

        it('keeps a spacer as wide as the divider in place of the divider of collapsed instructions', () => {
            fixture.detectChanges();
            const sidebar = fixture.nativeElement.querySelector('.editor-sidebar-right') as HTMLElement;
            expect(sidebar.querySelector('.separator')).toBeNull();

            collapse(CollapsableCodeEditorElement.Instructions);

            expect(divider('draggableIconForInstructions')).toBeNull();
            expect(sidebar.querySelector('.separator')).not.toBeNull();
            expect(sidebar.classList.contains('collapsed--horizontal')).toBe(true);
        });

        it('keeps the divider between the panels and the build output when the build output is collapsed', () => {
            fixture.detectChanges();

            collapse(CollapsableCodeEditorElement.BuildOutput);

            expect(divider('draggableIconForBuildOutput')).toBeNull();
            expect(divider('draggableIconForMain')).not.toBeNull();
        });

        it('marks the area below the build output of a tutor assessment', () => {
            fixture.detectChanges();
            expect(fixture.nativeElement.querySelector('.editor-assessment-bottom')).toBeNull();

            fixture.componentRef.setInput('isTutorAssessment', true);
            fixture.detectChanges();

            expect(fixture.nativeElement.querySelector('.editor-assessment-bottom')).not.toBeNull();
        });
    });

    describe('Maxima of the panels', () => {
        let observers: { callback: () => void; observed: Element[]; disconnect: Mock<() => void> }[];

        const content = (): HTMLElement => fixture.nativeElement.querySelector('.editor-main__content');
        // the resizable directives observe their panels too, so the observer of the area of the panels is the one that observes the content
        const observerOfContent = () => observers.find(({ observed }) => observed.includes(content()))!;
        const showAtWidth = (width: number) => {
            Object.defineProperty(content(), 'clientWidth', { configurable: true, value: width });
            observerOfContent().callback();
        };

        beforeEach(() => {
            observers = [];
            vi.stubGlobal(
                'ResizeObserver',
                class {
                    private readonly entry: (typeof observers)[number];

                    constructor(callback: () => void) {
                        this.entry = { callback, observed: [], disconnect: vi.fn() };
                        observers.push(this.entry);
                    }

                    observe(element: Element) {
                        this.entry.observed.push(element);
                    }

                    disconnect() {
                        this.entry.disconnect();
                    }
                },
            );
            vi.spyOn(window.screen, 'width', 'get').mockReturnValue(1920);
            fixture = TestBed.createComponent(CodeEditorGridComponent);
            comp = fixture.componentInstance;
            fixture.detectChanges();
            return fixture.whenStable();
        });

        afterEach(() => {
            vi.unstubAllGlobals();
            vi.restoreAllMocks();
        });

        it('measures the area of the panels again whenever it changes its size', () => {
            expect(observerOfContent()).toBeDefined();

            // the file browser may grow into what is left beside the editor (at least 300px) and the buffers: 1000px - 300px - 24px
            showAtWidth(1000);
            expect(comp['maxConstraints']().widthLeft).toBe(676);

            showAtWidth(800);
            expect(comp['maxConstraints']().widthLeft).toBe(476);
        });

        it('keeps the maxima of the last layout while the editor is hidden, and measures it when it is shown again', () => {
            showAtWidth(1000);

            // the page of the exam is hidden, so its area has no size, and the window is resized meanwhile
            showAtWidth(0);
            expect(comp['maxConstraints']().widthLeft).toBe(676);

            showAtWidth(900);
            expect(comp['maxConstraints']().widthLeft).toBe(576);
        });

        it('stops observing when the editor is destroyed', () => {
            const observer = observerOfContent();
            fixture.destroy();

            expect(observer.disconnect).toHaveBeenCalledOnce();
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
            return fixture.debugElement.query(By.css(`[data-testid="draggableIconFor${windowName}"]`));
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
});
