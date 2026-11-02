import { Mock, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
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

const dividerTestIds = ['draggableIconForMain', 'draggableIconForBuildOutput'];

describe('CodeEditorGridComponent', () => {
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

            expect(divider('draggableIconForMain')!.style.cursor).toBe('row-resize');
            expect(divider('draggableIconForBuildOutput')!.style.cursor).toBe('row-resize');
        });

        const inner = () => fixture.debugElement.query(By.css('.editor-sidebars--inner'));
        const outer = () => fixture.debugElement.query(By.css('.editor-sidebars--outer'));
        const panelsOf = (splitter: HTMLElement) => splitter.querySelectorAll(':scope > [data-testid="splitter-panel"]');
        /** The width (px) the page gives the column of the given selector. */
        const setWidth = (selector: string, width: number) =>
            vi.spyOn(fixture.nativeElement.querySelector(selector) as HTMLElement, 'getBoundingClientRect').mockReturnValue({ width } as DOMRect);

        afterEach(() => {
            ['code-editor-sidebars-inner', 'code-editor-sidebars-outer', 'code-editor-assessment-inner', 'code-editor-assessment-outer'].forEach((key) =>
                localStorage.removeItem(key),
            );
        });

        it('divides the file browser from the editor, and that area from the instructions, with two horizontal splitters', () => {
            fixture.detectChanges();

            const outerPanels = panelsOf(outer().nativeElement);
            expect(outerPanels).toHaveLength(2);
            expect(outerPanels[0].contains(inner().nativeElement)).toBe(true);
            expect(outerPanels[1].querySelector('.editor-sidebar-right')).not.toBeNull();
            const innerPanels = panelsOf(inner().nativeElement);
            expect(innerPanels[0].querySelector('.editor-sidebar-left')).not.toBeNull();
            expect(innerPanels[1].querySelector('.editor-center')).not.toBeNull();
            expect(outer().componentInstance.layout()).toBe('horizontal');
            expect(inner().componentInstance.layout()).toBe('horizontal');
            expect(fixture.nativeElement.querySelector('.rg-sidebar-left, .rg-sidebar-right')).toBeNull();
        });

        it('starts the exercise page and an assessment at their own widths, and with the widths chosen on an earlier visit', () => {
            fixture.detectChanges();
            expect(inner().componentInstance.panelSizes()).toEqual([34, 66]);
            expect(outer().componentInstance.panelSizes()).toEqual([70, 30]);

            fixture = TestBed.createComponent(CodeEditorGridComponent);
            fixture.componentRef.setInput('assessmentWorkspace', true);
            fixture.detectChanges();
            expect(inner().componentInstance.panelSizes()).toEqual([30, 70]);
            expect(outer().componentInstance.panelSizes()).toEqual([65, 35]);

            localStorage.setItem('code-editor-sidebars-inner', JSON.stringify([40, 60]));
            localStorage.setItem('code-editor-sidebars-outer', 'not json');
            fixture = TestBed.createComponent(CodeEditorGridComponent);
            fixture.detectChanges();
            expect(inner().componentInstance.panelSizes()).toEqual([40, 60]);
            expect(outer().componentInstance.panelSizes()).toEqual([70, 30]);
        });

        describe('dragging the file browser narrow', () => {
            let requests: unknown[];

            beforeEach(() => {
                fixture.detectChanges();
                requests = [];
                comp.collapseFileBrowserRequest.subscribe(() => requests.push(true));
            });

            it('asks for the file browser to collapse once it is dragged to at most 140px, the same on any width of the page', () => {
                setWidth('.editor-sidebar-left', 160);
                inner().triggerEventHandler('resizeEnd', [12, 88]);
                expect(requests).toHaveLength(0);

                setWidth('.editor-sidebar-left', 140);
                inner().triggerEventHandler('resizeEnd', [8, 92]);
                expect(requests).toHaveLength(1);
            });

            it('reopens the file browser at the width chosen before the drag, also after a reload', () => {
                setWidth('.editor-sidebar-left', 300);
                inner().triggerEventHandler('resizeEnd', [40, 60]);
                setWidth('.editor-sidebar-left', 50);
                inner().triggerEventHandler('resizeEnd', [5, 95]);
                fixture.detectChanges();

                expect(requests).toHaveLength(1);
                expect(JSON.parse(localStorage.getItem('code-editor-sidebars-inner')!)).toEqual([40, 60]);
                // A new array, so the splitter lets go of the drag and goes back to it
                expect(inner().componentInstance.panelSizes()).toEqual([40, 60]);
            });
        });

        describe('dragging the instructions narrow', () => {
            let requests: unknown[];

            beforeEach(() => {
                requests = [];
            });

            it('asks the exercise page to collapse the instructions once they are dragged to at most 140px, and keeps the width they had', () => {
                fixture.detectChanges();
                comp.collapseInstructionsRequest.subscribe(() => requests.push(true));
                setWidth('.editor-sidebar-right', 200);
                outer().triggerEventHandler('resizeEnd', [80, 20]);
                expect(requests).toHaveLength(0);

                setWidth('.editor-sidebar-right', 50);
                outer().triggerEventHandler('resizeEnd', [95, 5]);

                expect(requests).toHaveLength(1);
                expect(JSON.parse(localStorage.getItem('code-editor-sidebars-outer')!)).toEqual([80, 20]);
            });

            it('collapses the column of an assessment to its rail itself, and opens it again from the rail', () => {
                fixture.componentRef.setInput('assessmentWorkspace', true);
                fixture.detectChanges();
                comp.collapseInstructionsRequest.subscribe(() => requests.push(true));
                setWidth('.editor-sidebar-right', 50);

                outer().triggerEventHandler('resizeEnd', [95, 5]);
                fixture.detectChanges();

                expect(requests).toHaveLength(0);
                expect(comp.rightPanelIsCollapsed()).toBe(true);
                expect(localStorage.getItem('code-editor-assessment-outer')).toBeNull();
                const rail = fixture.nativeElement.querySelector('.editor-sidebar-right jhi-collapsed-panel-rail') as HTMLElement;
                expect(rail.querySelectorAll('button')).toHaveLength(1);

                rail.querySelector('button')!.click();
                fixture.detectChanges();

                expect(comp.rightPanelIsCollapsed()).toBe(false);
                expect(fixture.nativeElement.querySelector('.editor-sidebar-right jhi-collapsed-panel-rail')).toBeNull();
            });
        });

        it('puts the bottom area of an assessment under the file browser and the editor, and the instructions beside them', () => {
            fixture.componentRef.setInput('assessmentWorkspace', true);
            fixture.detectChanges();
            const bottom = fixture.nativeElement.querySelector('.editor-bottom') as HTMLElement;
            const outerPanels = panelsOf(outer().nativeElement);

            expect(outerPanels[0].contains(bottom)).toBe(true);
            expect(outerPanels[0].contains(inner().nativeElement)).toBe(true);
            expect(outerPanels[1].contains(bottom)).toBe(false);
        });

        it('shrinks a collapsed file browser to its rail', () => {
            fixture.detectChanges();
            expect(inner().nativeElement.classList).not.toContain('editor-sidebars--left-collapsed');

            collapse(CollapsableCodeEditorElement.FileBrowser);

            expect(comp.fileBrowserIsCollapsed()).toBe(true);
            expect(comp.rightPanelIsCollapsed()).toBe(false);
            expect(inner().nativeElement.classList).toContain('editor-sidebars--left-collapsed');
            expect(fixture.nativeElement.querySelector('.editor-sidebar-left').classList).toContain('collapsed--horizontal');
        });

        it('shrinks collapsed instructions to their rail, and leaves out their panel while there are none', () => {
            fixture.detectChanges();
            expect(outer().componentInstance.collapsedSecondSize()).toBeUndefined();

            collapse(CollapsableCodeEditorElement.Instructions);

            expect(comp.rightPanelIsCollapsed()).toBe(true);
            expect(comp.fileBrowserIsCollapsed()).toBe(false);
            expect(outer().componentInstance.collapsedSecondSize()).toBe('3rem');
            expect(fixture.nativeElement.querySelector('.editor-sidebar-right').classList).toContain('collapsed--horizontal');

            fixture.componentRef.setInput('showEditorSidebarRight', false);
            fixture.detectChanges();
            expect(outer().componentInstance.collapsedSecondSize()).toBe('0px');
        });

        it('reports which divider moved when a splitter finishes a drag', () => {
            fixture.detectChanges();
            const resized: unknown[] = [];
            comp.onResize.subscribe((type) => resized.push(type));
            setWidth('.editor-sidebar-left', 300);
            setWidth('.editor-sidebar-right', 300);

            inner().triggerEventHandler('resizeEnd', [40, 60]);
            outer().triggerEventHandler('resizeEnd', [60, 40]);

            expect(resized).toEqual([ResizeType.SIDEBAR_LEFT, ResizeType.SIDEBAR_RIGHT]);
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

    describe('Splitter of a filling grid', () => {
        const splitter = (): HTMLElement | null => fixture.nativeElement.querySelector('[data-testid="feedback-split"]');
        const area = (selector: string): HTMLElement | null => fixture.nativeElement.querySelector(selector);

        it('divides the editor from the bottom area with a splitter when it fills the page, and leaves out the old grips', () => {
            fixture.componentRef.setInput('fillHeight', true);
            fixture.detectChanges();

            expect(splitter()).not.toBeNull();
            expect(splitter()!.contains(area('.editor-main'))).toBe(true);
            expect(splitter()!.contains(area('.editor-bottom'))).toBe(true);
            // The line between the two areas is the splitter's, so there is no grip of the grid's own
            expect(area('[data-testid="draggableIconForMain"]')).toBeNull();
            expect(area('[data-testid="draggableIconForBuildOutput"]')).toBeNull();
        });

        it('keeps stacking the areas with the grips of the grid itself when it does not fill the page', () => {
            fixture.detectChanges();

            expect(splitter()).toBeNull();
            expect(area('.editor-main')).not.toBeNull();
            expect(area('.editor-bottom')).not.toBeNull();
            expect(area('[data-testid="draggableIconForMain"]')).not.toBeNull();
        });

        it('keeps the sidebars in their own splitters inside the one below the editor', () => {
            fixture.componentRef.setInput('fillHeight', true);
            fixture.detectChanges();

            expect(area('.editor-sidebars')).not.toBeNull();
            expect(splitter()!.contains(area('.editor-sidebars'))).toBe(true);
        });

        it('collapses the bottom area to its header through the splitter', () => {
            fixture.componentRef.setInput('fillHeight', true);
            fixture.detectChanges();
            expect(splitter()!.classList).not.toContain('feedback-split--collapsed');

            comp.toggleCollapse(
                { event: { type: 'click', target: { blur: () => {} } } as unknown as PointerEvent, horizontal: false } as InteractableEvent,
                CollapsableCodeEditorElement.BuildOutput,
            );
            fixture.changeDetectorRef.detectChanges();

            expect(comp.buildOutputIsCollapsed()).toBe(true);
            expect(splitter()!.classList).toContain('feedback-split--collapsed');
        });

        it('collapses the bottom area when the splitter asks for it, and keeps it collapsed on a second request', () => {
            fixture.componentRef.setInput('fillHeight', true);
            fixture.detectChanges();

            fixture.debugElement.query(By.css('jhi-feedback-split')).triggerEventHandler('collapseRequested');
            fixture.changeDetectorRef.detectChanges();

            expect(comp.buildOutputIsCollapsed()).toBe(true);
            expect(splitter()!.classList).toContain('feedback-split--collapsed');

            comp.collapseBuildOutput();
            expect(comp.buildOutputIsCollapsed()).toBe(true);
        });

        it('reports a resize of the splitter like one of the old grip', () => {
            fixture.componentRef.setInput('fillHeight', true);
            fixture.detectChanges();
            const resized: unknown[] = [];
            comp.onResize.subscribe((type) => resized.push(type));

            fixture.debugElement.query(By.css('jhi-feedback-split')).triggerEventHandler('resizeEnd');

            expect(resized).toEqual([ResizeType.MAIN_BOTTOM]);
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
            const measure = vi.spyOn(comp as unknown as { recomputeMaxConstraints: () => void }, 'recomputeMaxConstraints');

            showAtWidth(1000);
            showAtWidth(800);

            expect(measure).toHaveBeenCalledTimes(2);
        });

        it('keeps the maxima of the last layout while the editor is hidden, and measures it when it is shown again', () => {
            showAtWidth(1000);
            const lastLayout = comp['maxConstraints']();

            // the page of the exam is hidden, so its area has no size, and the window is resized meanwhile
            showAtWidth(0);
            expect(comp['maxConstraints']()).toBe(lastLayout);

            showAtWidth(900);
            expect(comp['maxConstraints']()).not.toBe(lastLayout);
        });

        it('stops observing when the editor is destroyed', () => {
            const observer = observerOfContent();
            fixture.destroy();

            expect(observer.disconnect).toHaveBeenCalledOnce();
        });
    });

    describe('Hide draggable icons', () => {
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
