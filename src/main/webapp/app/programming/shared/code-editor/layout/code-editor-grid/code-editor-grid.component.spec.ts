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

        it('divides the file browser, the editor and the instructions with one horizontal splitter', () => {
            fixture.detectChanges();
            const sidebars = fixture.nativeElement.querySelector('.editor-sidebars') as HTMLElement;

            expect(fixture.debugElement.query(By.css('.editor-sidebars')).componentInstance.layout).toBe('horizontal');
            const panels = sidebars.querySelectorAll(':scope > [data-pc-section="panel"]');
            expect(panels).toHaveLength(3);
            expect(panels[0].querySelector('.editor-sidebar-left')).not.toBeNull();
            expect(panels[1].querySelector('.editor-center')).not.toBeNull();
            expect(panels[2].querySelector('.editor-sidebar-right')).not.toBeNull();
            // The dividers between them are the splitter's gutters, not handles of the grid
            expect(sidebars.querySelectorAll(':scope > [data-pc-section="gutter"]')).toHaveLength(2);
            expect(fixture.nativeElement.querySelector('.rg-sidebar-left, .rg-sidebar-right')).toBeNull();
        });

        describe('dragging the file browser narrow', () => {
            const splitter = () => fixture.debugElement.query(By.css('.editor-sidebars'));
            const startDrag = (sizes: number[]) => splitter().triggerEventHandler('onResizeStart', { sizes });
            const endDrag = (sizes: number[]) => splitter().triggerEventHandler('onResizeEnd', { sizes });
            const dragTo = (before: number[], after: number[]) => {
                startDrag(before);
                endDrag(after);
            };
            /** The page gives the splitter this width (px); its two dividers take 24 px of it. */
            const setSplitterWidth = (width: number) => {
                vi.spyOn(splitter().nativeElement as HTMLElement, 'getBoundingClientRect').mockReturnValue({ width } as DOMRect);
            };
            let requests: unknown[];

            beforeEach(() => {
                localStorage.removeItem('code-editor-sidebars');
                fixture.detectChanges();
                requests = [];
                comp.collapseFileBrowserRequest.subscribe(() => requests.push(true));
                setSplitterWidth(1024);
            });

            afterEach(() => localStorage.removeItem('code-editor-sidebars'));

            it('asks for the file browser to collapse once it is dragged to at most 140px, also on the very first drag', () => {
                // 10% of the 1000px the panels share
                dragTo([30, 40, 30], [10, 60, 30]);

                expect(requests).toHaveLength(1);
            });

            it('collapses at the same width on a wide page as on a narrow one', () => {
                setSplitterWidth(1824);
                // 1800px to share: 7% is 126px, 9% is 162px
                dragTo([20, 45, 35], [9, 56, 35]);
                expect(requests).toHaveLength(0);

                dragTo([20, 45, 35], [7, 58, 35]);
                expect(requests).toHaveLength(1);
            });

            it('reopens the file browser at the width it had before the drag, also after a reload', () => {
                comp.collapseFileBrowserRequest.subscribe(() => collapse(CollapsableCodeEditorElement.FileBrowser));

                dragTo([30, 40, 30], [10, 60, 30]);

                expect(JSON.parse(localStorage.getItem('code-editor-sidebars')!)).toEqual([30, 40, 30]);
                const panels = fixture.nativeElement.querySelectorAll('.editor-sidebars > [data-pc-section="panel"]') as NodeListOf<HTMLElement>;
                expect(panels[0].style.flexBasis).toContain('30%');
            });

            it('leaves a file browser that is wide enough, and one that is collapsed already, as it is', () => {
                dragTo([30, 40, 30], [20, 45, 35]);
                expect(requests).toHaveLength(0);

                collapse(CollapsableCodeEditorElement.FileBrowser);
                dragTo([20, 45, 35], [10, 60, 30]);
                expect(requests).toHaveLength(0);
            });

            it('does not ask for a collapse when the divider beside the instructions moved', () => {
                dragTo([10, 55, 35], [10, 70, 20]);

                expect(requests).toHaveLength(0);
            });

            describe('dragging the instructions narrow', () => {
                let instructionsRequests: unknown[];

                beforeEach(() => {
                    instructionsRequests = [];
                    comp.collapseInstructionsRequest.subscribe(() => instructionsRequests.push(true));
                });

                it('asks for the instructions to collapse once they are dragged to at most 140px, and keeps the width they had', () => {
                    // 5% of the 1000px the panels share is 50px
                    dragTo([24, 46, 30], [24, 71, 5]);

                    expect(instructionsRequests).toHaveLength(1);
                    expect(requests).toHaveLength(0);
                    expect(JSON.parse(localStorage.getItem('code-editor-sidebars')!)).toEqual([24, 46, 30]);
                });

                it('leaves instructions that are wide enough, and ones that are collapsed already, as they are', () => {
                    dragTo([24, 46, 30], [24, 56, 20]);
                    expect(instructionsRequests).toHaveLength(0);

                    collapse(CollapsableCodeEditorElement.Instructions);
                    dragTo([24, 56, 20], [24, 71, 5]);
                    expect(instructionsRequests).toHaveLength(0);
                });

                describe('in an assessment', () => {
                    const outer = () => fixture.debugElement.query(By.css('.editor-sidebars--outer'));
                    const inner = () => fixture.debugElement.query(By.css('.editor-sidebars--inner'));

                    beforeEach(() => {
                        fixture.componentRef.setInput('assessmentWorkspace', true);
                        fixture.detectChanges();
                        // The page gives the splitters this width (px); a divider takes 12 px of it
                        vi.spyOn(outer().nativeElement as HTMLElement, 'getBoundingClientRect').mockReturnValue({ width: 1012 } as DOMRect);
                        vi.spyOn(inner().nativeElement as HTMLElement, 'getBoundingClientRect').mockReturnValue({ width: 712 } as DOMRect);
                    });

                    it('puts the bottom area under the file browser and the editor, and the instructions beside them', () => {
                        const bottom = fixture.nativeElement.querySelector('.editor-bottom') as HTMLElement;
                        const outerPanels = fixture.nativeElement.querySelectorAll('.editor-sidebars--outer > [data-pc-section="panel"]');

                        expect(outerPanels).toHaveLength(2);
                        expect(outerPanels[0].contains(bottom)).toBe(true);
                        expect(outerPanels[0].contains(fixture.nativeElement.querySelector('.editor-sidebar-left'))).toBe(true);
                        expect(outerPanels[0].contains(fixture.nativeElement.querySelector('.editor-center'))).toBe(true);
                        expect(outerPanels[1].querySelector('.editor-sidebar-right')).not.toBeNull();
                        expect(outerPanels[1].contains(bottom)).toBe(false);
                    });

                    it('collapses the column of the instructions to its rail when it is dragged to at most 140px, and opens it again from the rail', () => {
                        outer().triggerEventHandler('onResizeStart', { sizes: [65, 35] });
                        // 5% of the 1000px the panels share is 50px
                        outer().triggerEventHandler('onResizeEnd', { sizes: [95, 5] });
                        fixture.detectChanges();

                        // The grid collapses the column itself, the page has nothing to do
                        expect(instructionsRequests).toHaveLength(0);
                        expect(comp.rightPanelIsCollapsed()).toBe(true);
                        expect(JSON.parse(localStorage.getItem('code-editor-assessment-outer')!)).toEqual([65, 35]);
                        const rail = fixture.nativeElement.querySelector('.editor-sidebar-right jhi-collapsed-panel-rail') as HTMLElement;
                        expect(rail.querySelectorAll('button')).toHaveLength(1);

                        rail.querySelector('button')!.click();
                        fixture.detectChanges();

                        expect(comp.rightPanelIsCollapsed()).toBe(false);
                        expect(fixture.nativeElement.querySelector('.editor-sidebar-right jhi-collapsed-panel-rail')).toBeNull();
                    });

                    it('asks for the file browser to collapse once it is dragged to at most 140px', () => {
                        inner().triggerEventHandler('onResizeStart', { sizes: [30, 70] });
                        // 10% of the 700px the panels share is 70px
                        inner().triggerEventHandler('onResizeEnd', { sizes: [10, 90] });

                        expect(requests).toHaveLength(1);
                        expect(JSON.parse(localStorage.getItem('code-editor-assessment-inner')!)).toEqual([30, 70]);
                    });
                });
            });
        });

        it('shrinks a collapsed file browser to its header strip and keeps its divider in place', () => {
            fixture.detectChanges();
            const sidebars = fixture.nativeElement.querySelector('.editor-sidebars') as HTMLElement;
            expect(sidebars.classList).not.toContain('editor-sidebars--left-collapsed');

            collapse(CollapsableCodeEditorElement.FileBrowser);

            expect(comp.fileBrowserIsCollapsed()).toBe(true);
            expect(comp.rightPanelIsCollapsed()).toBe(false);
            expect(sidebars.classList).toContain('editor-sidebars--left-collapsed');
            expect(fixture.nativeElement.querySelector('.editor-sidebar-left').classList).toContain('collapsed--horizontal');
        });

        it('shrinks collapsed instructions to their header strip and keeps their divider in place', () => {
            fixture.detectChanges();
            const sidebars = fixture.nativeElement.querySelector('.editor-sidebars') as HTMLElement;

            collapse(CollapsableCodeEditorElement.Instructions);

            expect(comp.rightPanelIsCollapsed()).toBe(true);
            expect(comp.fileBrowserIsCollapsed()).toBe(false);
            expect(sidebars.classList).toContain('editor-sidebars--right-collapsed');
            expect(fixture.nativeElement.querySelector('.editor-sidebar-right').classList).toContain('collapsed--horizontal');
        });

        it('leaves out the instructions panel and its divider while there are no instructions', () => {
            fixture.componentRef.setInput('showEditorSidebarRight', true);
            fixture.detectChanges();
            const sidebars = fixture.nativeElement.querySelector('.editor-sidebars') as HTMLElement;
            expect(sidebars.classList).not.toContain('editor-sidebars--right-hidden');

            fixture.componentRef.setInput('showEditorSidebarRight', false);
            fixture.detectChanges();

            expect(sidebars.classList).toContain('editor-sidebars--right-hidden');
        });

        it('lets every panel shrink to the same least share, which PrimeNG applies to the panels around the dragged divider', () => {
            fixture.detectChanges();
            // PrimeNG applies only the first two to them, so a larger second one would stop the instructions from shrinking
            expect(comp['sidebarMinSizes']).toEqual([5, 5, 5]);
            expect(comp['assessmentMinSizes']).toEqual([5, 5]);
            expect(comp['sidebarSizes']).toEqual([24, 46, 30]);
        });

        it('reports which divider moved when the splitter finishes a drag', () => {
            fixture.detectChanges();
            const resized: unknown[] = [];
            comp.onResize.subscribe((type) => resized.push(type));
            const splitter = fixture.debugElement.query(By.css('.editor-sidebars'));
            // A page that is wide enough for the file browser never to be narrow enough to collapse
            vi.spyOn(splitter.nativeElement as HTMLElement, 'getBoundingClientRect').mockReturnValue({ width: 1024 } as DOMRect);

            // The first drag has nothing to compare with and counts as the instructions' divider; then the file browser's share changes
            splitter.triggerEventHandler('onResizeEnd', { originalEvent: new MouseEvent('mouseup'), sizes: [24, 46, 30] });
            splitter.triggerEventHandler('onResizeEnd', { originalEvent: new MouseEvent('mouseup'), sizes: [30, 40, 30] });
            splitter.triggerEventHandler('onResizeEnd', { originalEvent: new MouseEvent('mouseup'), sizes: [30, 35, 35] });

            expect(resized).toEqual([ResizeType.SIDEBAR_RIGHT, ResizeType.SIDEBAR_LEFT, ResizeType.SIDEBAR_RIGHT]);
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

        it('keeps the sidebars in their own splitter beside the one below the editor', () => {
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
