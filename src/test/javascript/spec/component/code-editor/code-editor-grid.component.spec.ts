import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ArtemisTestModule } from '../../test.module';
import { CodeEditorGridComponent } from 'app/exercises/programming/shared/code-editor/layout/code-editor-grid.component';
import { Interactable } from '@interactjs/core/Interactable';
import { InteractableEvent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser.component';

const fileBrowserWindowName = 'FileBrowser';
const instructionsWindowName = 'Instructions';
const buildOutputWindowName = 'BuildOutput';

describe('CodeEditorGridComponent', () => {
    let comp: CodeEditorGridComponent;
    let fixture: ComponentFixture<CodeEditorGridComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [CodeEditorGridComponent],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CodeEditorGridComponent);
                comp = fixture.componentInstance;
            });
    });

    describe('Hide draggable icons', () => {
        it('should hide draggable icon for file browser', () => {
            executeHideDraggableIconTestForWindow(fileBrowserWindowName);
        });

        // right panel = Instruction / Problem Statement
        it('should hide draggable icon for right panel', () => {
            executeHideDraggableIconTestForWindow(instructionsWindowName);
        });

        it('should hide draggable icon for build output', () => {
            executeHideDraggableIconTestForWindow(buildOutputWindowName);
        });

        const executeHideDraggableIconTestForWindow = (windowName: string) => {
            fixture.detectChanges();
            let draggableIconForWindow = getDebugElement(windowName);

            expect(draggableIconForWindow).not.toBe(null);

            const resizable = () => {};
            const windowInteractable: Interactable = { target: '.resizable-' + windowName.toLowerCase(), resizable } as Interactable;

            const blur = () => {};
            const pointerEvent: PointerEvent = { type: 'click', target: { blur } as unknown as HTMLElement } as unknown as PointerEvent;

            const windowCollapseEvent: InteractableEvent = { event: pointerEvent, horizontal: true, interactable: windowInteractable };

            expectWindowToBeCollapsed(windowName, false);

            comp.toggleCollapse(windowCollapseEvent);

            fixture.detectChanges();

            draggableIconForWindow = getDebugElement(windowName);
            expectWindowToBeCollapsed(windowName, true);
            expect(draggableIconForWindow).toBe(null);
        };

        const getDebugElement = (windowName: string) => {
            return fixture.debugElement.query(By.css('#draggableIconFor' + windowName));
        };

        const expectAllWindowsToNotBeCollapsed = () => {
            expect(comp.fileBrowserIsCollapsed).toBe(false);
            expect(comp.rightPanelIsCollapsed).toBe(false);
            expect(comp.buildOutputIsCollapsed).toBe(false);
        };

        const expectWindowToBeCollapsed = (windowName: string, collapsed: boolean) => {
            switch (windowName) {
                case fileBrowserWindowName: {
                    if (collapsed) {
                        expect(comp.fileBrowserIsCollapsed).toBe(true);
                        expect(comp.rightPanelIsCollapsed).toBe(false);
                        expect(comp.buildOutputIsCollapsed).toBe(false);
                    } else {
                        expectAllWindowsToNotBeCollapsed();
                    }
                    break;
                }
                case instructionsWindowName: {
                    if (collapsed) {
                        expect(comp.fileBrowserIsCollapsed).toBe(false);
                        expect(comp.rightPanelIsCollapsed).toBe(true);
                        expect(comp.buildOutputIsCollapsed).toBe(false);
                    } else {
                        expectAllWindowsToNotBeCollapsed();
                    }
                    break;
                }
                case buildOutputWindowName: {
                    if (collapsed) {
                        expect(comp.fileBrowserIsCollapsed).toBe(false);
                        expect(comp.rightPanelIsCollapsed).toBe(false);
                        expect(comp.buildOutputIsCollapsed).toBe(true);
                    } else {
                        expectAllWindowsToNotBeCollapsed();
                    }
                    break;
                }
            }
        };
    });
});
