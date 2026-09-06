import { vi } from 'vitest';
import type { ApollonOptions } from '@tumaet/apollon';

type MockApollonOptions = Pick<ApollonOptions, 'model' | 'collaboration' | 'labels' | 'scrollLock'>;

const { MockApollonEditor } = vi.hoisted(() => {
    const deepClone = (obj: any): any => (obj ? JSON.parse(JSON.stringify(obj)) : {});

    class MockApollonEditorClass {
        static exportModelAsSvg = vi.fn().mockResolvedValue({
            svg: '<svg width="800" height="400" viewBox="0 0 800 400"></svg>',
            clip: { x: 0, y: 0, width: 800, height: 400 },
        });

        _model: any;
        _options: MockApollonOptions | undefined;
        _subscriptions = new Map<number, (model: any) => void>();
        _subscriptionCounter = 0;
        _broadcastCallback: ((patch: string) => void) | undefined;
        _destroyed = false;
        _regionElements = new Map<string, HTMLElement>();
        _container: HTMLElement | undefined;

        subscribeToModelChange = vi.fn((callback: (model: any) => void) => {
            const id = ++this._subscriptionCounter;
            this._subscriptions.set(id, callback);
            return id;
        });

        unsubscribe = vi.fn((id: number) => {
            this._subscriptions.delete(id);
        });

        sendBroadcastMessage = vi.fn((callback: (patch: string) => void) => {
            this._broadcastCallback = callback;
        });

        receiveBroadcastedMessage = vi.fn();

        broadcastFullState = vi.fn();

        setLocalAwarenessUser = vi.fn();

        setLabels = vi.fn();

        setScrollLock = vi.fn();

        fitView = vi.fn();

        updateControl = vi.fn();

        getRegionElement = vi.fn((region: string) => {
            let element = this._regionElements.get(region);
            if (!element) {
                element = document.createElement('div');
                this._regionElements.set(region, element);
                this._container?.append(element);
            }
            return element;
        });

        releaseRegionElement = vi.fn();

        destroy = vi.fn(() => {
            this._destroyed = true;
            this._subscriptions.clear();
        });

        nextRender = Promise.resolve();

        constructor(container: HTMLElement, options?: MockApollonOptions) {
            this._container = container;
            this._options = options;
            this._model = options?.model ? deepClone(options.model) : {};
        }

        get model() {
            return this._model;
        }

        set model(value: any) {
            this._model = value;
            this._subscriptions.forEach((callback: (model: any) => void) => callback(this._model));
        }
    }

    return { MockApollonEditor: MockApollonEditorClass };
});

vi.mock('@tumaet/apollon', async (importOriginal) => {
    const actual = await importOriginal<typeof import('@tumaet/apollon')>();
    return {
        ...actual,
        ApollonEditor: MockApollonEditor,
    };
});

import { Course } from 'app/course/shared/entities/course.model';
import { By } from '@angular/platform-browser';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, signal } from '@angular/core';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { ApollonDiagram } from 'app/modeling/shared/entities/apollon-diagram.model';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { UMLDiagramType, UMLModel } from '@tumaet/apollon';
import { ModelingEditorComponent } from 'app/modeling/shared/modeling-editor/modeling-editor.component';
import testClassDiagram from 'test/helpers/sample/modeling/test-models/class-diagram.json';
import { deepClone } from 'app/foundation/util/deep-clone.util';
import { ModelingExplanationEditorComponent } from 'app/modeling/shared/modeling-explanation-editor/modeling-explanation-editor.component';
import { provideHttpClient } from '@angular/common/http';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ModelingEditorTopLeftDirective } from 'app/modeling/shared/modeling-editor/modeling-editor-top-left.directive';

@Component({
    template: `
        <jhi-modeling-editor [diagramType]="diagramType()">
            <div modelingEditorTopLeft data-testid="projected-top-left">Diagram type</div>
        </jhi-modeling-editor>
    `,
    imports: [ModelingEditorComponent, ModelingEditorTopLeftDirective],
})
class ModelingEditorTopLeftHostComponent {
    protected readonly diagramType = signal<UMLDiagramType>(UMLDiagramType.ClassDiagram);

    setDiagramType(type: UMLDiagramType): void {
        this.diagramType.set(type);
    }
}

describe('ModelingEditorComponent', () => {
    let fixture: ComponentFixture<ModelingEditorComponent>;
    let component: ModelingEditorComponent;

    const course = { id: 123 } as Course;
    const diagram = new ApollonDiagram(UMLDiagramType.ClassDiagram, course.id!);
    const classDiagram = deepClone(testClassDiagram) as unknown as UMLModel;
    const route = { params: of({ id: 1, courseId: 123 }), snapshot: { paramMap: convertToParamMap({ courseId: course.id }) } } as any as ActivatedRoute;
    const originalFullscreenEnabled = Object.getOwnPropertyDescriptor(document, 'fullscreenEnabled');

    beforeEach(() => {
        Object.defineProperty(document, 'fullscreenEnabled', { configurable: true, value: true });
        diagram.id = 1;
        diagram.jsonRepresentation = JSON.stringify(classDiagram);

        vi.spyOn(console, 'error').mockImplementation(() => {});

        TestBed.configureTestingModule({
            imports: [ModelingEditorComponent, ModelingExplanationEditorComponent, ModelingEditorTopLeftHostComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });

        fixture = TestBed.createComponent(ModelingEditorComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        if (component) {
            component.ngOnDestroy();
        }
        fixture?.destroy();
        if (originalFullscreenEnabled) {
            Object.defineProperty(document, 'fullscreenEnabled', originalFullscreenEnabled);
        } else {
            Reflect.deleteProperty(document, 'fullscreenEnabled');
        }
        vi.restoreAllMocks();
    });

    it('initializes Apollon with the normalized model and subscriptions', async () => {
        fixture.componentRef.setInput('umlModel', classDiagram);
        fixture.detectChanges();

        await component.ngAfterViewInit();
        const editor = component['apollonEditor'] as unknown as InstanceType<typeof MockApollonEditor>;
        expect(editor).toBeDefined();
        expect(editor.subscribeToModelChange).toHaveBeenCalled();
        expect(editor.sendBroadcastMessage).toHaveBeenCalled();
        expect(editor._model.nodes).toBeInstanceOf(Array);
        expect(editor._model.edges).toBeInstanceOf(Array);
        expect(editor._model.nodes.length).toBeGreaterThan(0);
        expect(editor._model.edges.length).toBeGreaterThan(0);
        expect(editor._model.assessments).toEqual({});
    });

    it('should isolate the input model from Apollon normalization and assessment removal', async () => {
        const inputModel = deepClone(classDiagram);
        const assessment = {
            modelElementId: 'element-1',
            elementType: 'Class',
            score: 1,
        };
        inputModel.assessments = { [assessment.modelElementId]: assessment };

        fixture.componentRef.setInput('umlModel', inputModel);
        fixture.detectChanges();
        await component.ngAfterViewInit();

        expect(inputModel.assessments).toEqual({ [assessment.modelElementId]: assessment });
        expect(component['apollonEditor']?.model.assessments).toEqual({});
        expect(component.getCurrentModel()).not.toBe(component['apollonEditor']?.model);
    });

    it('exports read-only diagrams without mounting an interactive editor or its frame', async () => {
        fixture.componentRef.setInput('umlModel', classDiagram);
        fixture.componentRef.setInput('readOnly', true);
        fixture.detectChanges();
        await component.ngAfterViewInit();
        fixture.detectChanges();

        expect(fixture.debugElement.query(By.css('.modeling-editor__frame'))).toBeNull();
        expect(fixture.debugElement.query(By.css('.apollon-container'))).toBeNull();
        expect(fixture.debugElement.query(By.css('.readonly-diagram.artemis-apollon-theme'))).not.toBeNull();
        expect(fixture.nativeElement.querySelector('.readonly-diagram svg')).not.toBeNull();
        expect(component['apollonEditor']).toBeUndefined();
        expect(MockApollonEditor.exportModelAsSvg).toHaveBeenCalledWith(expect.objectContaining({ assessments: {} }));
    });

    it('ngOnDestroy', async () => {
        fixture.componentRef.setInput('umlModel', classDiagram);
        fixture.detectChanges();
        await component.ngAfterViewInit();

        const editor = component['apollonEditor'] as any;
        component.ngOnDestroy();
        expect(component['apollonEditor']).toBeUndefined();
        expect(editor.destroy).toHaveBeenCalled();
    });

    it('should wait for the local user before mounting a collaborative editor', () => {
        const collaborationUser = { id: 'student1', name: 'Student One', color: '#123456' };
        fixture.componentRef.setInput('umlModel', classDiagram);
        fixture.componentRef.setInput('collaborationEnabled', true);
        fixture.detectChanges();

        expect(component['apollonEditor']).toBeUndefined();

        fixture.componentRef.setInput('collaborationUser', collaborationUser);
        fixture.detectChanges();

        const editor = component['apollonEditor'] as unknown as InstanceType<typeof MockApollonEditor>;
        expect(editor).toBeDefined();
        expect(editor._options?.collaboration).toEqual({
            enabled: true,
            user: collaborationUser,
            showPresence: true,
            showCursors: true,
            showSelectionHighlights: true,
            showFollow: true,
        });
    });

    it('updates the mounted editor when the model input changes', async () => {
        fixture.componentRef.setInput('umlModel', classDiagram);
        fixture.detectChanges();
        await component.ngAfterViewInit();

        const editor = component['apollonEditor'] as unknown as InstanceType<typeof MockApollonEditor>;
        const changedModel = deepClone(editor.model);
        changedModel.nodes = [];
        changedModel.edges = [];
        fixture.componentRef.setInput('umlModel', changedModel);
        fixture.detectChanges();

        expect(editor.model.nodes).toEqual([]);
        expect(editor.model.edges).toEqual([]);
    });

    it('should not show save indicator without savedStatus set', async () => {
        fixture.componentRef.setInput('savedStatus', undefined);
        fixture.componentRef.setInput('readOnly', false);
        fixture.componentRef.setInput('umlModel', classDiagram);
        fixture.detectChanges();
        await component.ngAfterViewInit();

        const statusHint = fixture.debugElement.query(By.css('.modeling-editor__status-island'));
        expect(statusHint).toBeNull();
    });

    it('should not show save indicator in read only mode', () => {
        fixture.componentRef.setInput('savedStatus', { isSaving: false, isChanged: false });
        fixture.componentRef.setInput('readOnly', true);
        fixture.detectChanges();

        const statusHint = fixture.debugElement.query(By.css('.modeling-editor__status-island'));
        expect(statusHint).toBeNull();
    });

    it('should keep the save indicator available in fullscreen mode', () => {
        fixture.componentRef.setInput('savedStatus', { isSaving: false, isChanged: false });
        component.fullscreenActive.set(true);
        fixture.detectChanges();

        const statusHint = fixture.debugElement.query(By.css('.modeling-editor__status-island'));
        expect(statusHint).not.toBeNull();
    });

    it('should mount save status as its own top-left island instead of grouping it with actions', async () => {
        fixture.componentRef.setInput('umlModel', classDiagram);
        fixture.componentRef.setInput('savedStatus', { isSaving: false, isChanged: false });
        fixture.detectChanges();
        await fixture.whenStable();

        const editor = component['apollonEditor'] as unknown as InstanceType<typeof MockApollonEditor>;
        const status = fixture.nativeElement.querySelector('.modeling-editor__status-island');
        const actions = fixture.nativeElement.querySelector('.modeling-editor__actions');

        expect(editor.getRegionElement).toHaveBeenCalledWith('top-left');
        expect(editor._regionElements.get('top-left')?.contains(status)).toBe(true);
        expect(actions.contains(status)).toBe(false);
    });

    it('should mount Artemis actions into Apollons measured top-right island and release it on destroy', async () => {
        fixture.componentRef.setInput('umlModel', classDiagram);
        fixture.detectChanges();
        await fixture.whenStable();

        const editor = component['apollonEditor'] as unknown as InstanceType<typeof MockApollonEditor>;
        const actions = fixture.nativeElement.querySelector('.modeling-editor__actions');

        expect(editor.getRegionElement).toHaveBeenCalledExactlyOnceWith('top-right');
        expect(editor._regionElements.get('top-right')?.contains(actions)).toBe(true);
        const actionButtons = actions.querySelectorAll('.artemis-apollon-chrome-action.apollon-chrome-iconbtn');
        expect(actionButtons).toHaveLength(2);
        expect([...actionButtons].every((button) => !button.hasAttribute('data-slot'))).toBe(true);

        component.ngOnDestroy();
        expect(editor.releaseRegionElement).toHaveBeenCalledExactlyOnceWith('top-right');
    });

    it('should keep a resizable right-rail problem-statement island mounted across disclosure state in fullscreen', async () => {
        fixture.componentRef.setInput('umlModel', classDiagram);
        fixture.componentRef.setInput('problemStatement', '## Your task\n\nModel the domain.');
        fixture.detectChanges();
        await fixture.whenStable();

        const editor = component['apollonEditor'] as unknown as InstanceType<typeof MockApollonEditor>;
        const island = fixture.nativeElement.querySelector('jhi-apollon-rail-disclosure') as HTMLElement;
        const disclosure = island.querySelector('[data-testid="modeling-editor-problem-statement"]') as HTMLButtonElement;
        const horizontalResizer = island.querySelector('.apollon-rail-disclosure__resizer--left') as HTMLElement;
        const verticalResizer = island.querySelector('.apollon-rail-disclosure__resizer--bottom') as HTMLElement;
        const scheduleChromePlacement = vi.spyOn(component as any, 'scheduleChromePlacement');
        expect(island.hidden).toBe(true);
        expect(editor.getRegionElement).not.toHaveBeenCalledWith('right-rail');

        component.fullscreenActive.set(true);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(island.hidden).toBe(false);
        expect(disclosure.getAttribute('aria-expanded')).toBe('false');
        expect(disclosure.classList.contains('artemis-apollon-chrome-action')).toBe(true);
        expect(disclosure.classList.contains('apollon-chrome-iconbtn')).toBe(true);
        expect(disclosure.hasAttribute('data-slot')).toBe(false);
        expect(editor.getRegionElement).toHaveBeenCalledWith('right-rail');
        expect(editor.updateControl).toHaveBeenCalledWith('apollon:host:right-rail', { style: { overflow: 'visible' } });

        const panel = island.querySelector('.apollon-rail-disclosure__panel') as HTMLElement;
        expect(editor._regionElements.get('right-rail')?.contains(island)).toBe(true);
        expect(panel.hidden).toBe(true);

        scheduleChromePlacement.mockClear();
        disclosure.focus();
        disclosure.click();
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.problemStatementVisible()).toBe(true);
        expect(scheduleChromePlacement).toHaveBeenCalled();
        expect(disclosure.getAttribute('aria-expanded')).toBe('true');
        expect(panel.hidden).toBe(false);
        expect(island.querySelector('.markdown-preview')).not.toBeNull();
        expect(horizontalResizer.getAttribute('role')).toBe('separator');
        expect(horizontalResizer.getAttribute('aria-orientation')).toBe('vertical');
        expect(horizontalResizer.getAttribute('aria-valuemin')).toBe('288');
        expect(horizontalResizer.getAttribute('aria-valuemax')).toBe('704');
        expect(verticalResizer.getAttribute('role')).toBe('separator');
        expect(verticalResizer.getAttribute('aria-orientation')).toBe('horizontal');
        expect(verticalResizer.getAttribute('aria-valuemin')).toBe('224');
        expect(verticalResizer.getAttribute('aria-valuemax')).toBe('720');

        const panelRect = vi.spyOn(panel, 'getBoundingClientRect').mockReturnValue({ width: 416, height: 480 } as DOMRect);
        horizontalResizer.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft', bubbles: true }));
        fixture.detectChanges();
        expect(panel.style.width).toBe('432px');
        expect(panel.style.height).toBe('480px');
        expect(horizontalResizer.getAttribute('aria-valuenow')).toBe('432');

        panelRect.mockReturnValue({ width: 432, height: 480 } as DOMRect);
        verticalResizer.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
        fixture.detectChanges();
        expect(panel.style.width).toBe('432px');
        expect(panel.style.height).toBe('496px');
        expect(verticalResizer.getAttribute('aria-valuenow')).toBe('496');

        scheduleChromePlacement.mockClear();
        disclosure.click();
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.problemStatementVisible()).toBe(false);
        expect(scheduleChromePlacement).toHaveBeenCalled();
        expect(panel.hidden).toBe(true);
        expect(editor.releaseRegionElement).not.toHaveBeenCalledWith('right-rail');
        expect(editor._regionElements.get('right-rail')?.contains(island)).toBe(true);
        expect(document.activeElement).toBe(disclosure);

        component.ngOnDestroy();
        expect(editor.releaseRegionElement.mock.calls.filter(([region]) => region === 'right-rail')).toHaveLength(1);
    });

    it('should preserve the projected top-left control when a diagram-type change remounts Apollon', async () => {
        const hostFixture = TestBed.createComponent(ModelingEditorTopLeftHostComponent);
        hostFixture.detectChanges();
        await hostFixture.whenStable();

        const editorDebugElement = hostFixture.debugElement.query(By.directive(ModelingEditorComponent));
        const hostEditor = editorDebugElement.componentInstance as ModelingEditorComponent;
        const firstEditor = hostEditor['apollonEditor'] as unknown as InstanceType<typeof MockApollonEditor>;
        const island = hostFixture.nativeElement.querySelector('.modeling-editor__top-left');
        const projectedControl = hostFixture.nativeElement.querySelector('[data-testid="projected-top-left"]');

        expect(firstEditor._regionElements.get('top-left')?.contains(island)).toBe(true);
        expect(island.contains(projectedControl)).toBe(true);

        hostFixture.componentInstance.setDiagramType(UMLDiagramType.ActivityDiagram);
        hostFixture.detectChanges();
        await hostFixture.whenStable();

        const replacementEditor = hostEditor['apollonEditor'] as unknown as InstanceType<typeof MockApollonEditor>;
        expect(replacementEditor).not.toBe(firstEditor);
        expect(firstEditor.releaseRegionElement).toHaveBeenCalledWith('top-left');
        expect(firstEditor.destroy).toHaveBeenCalledOnce();
        expect(replacementEditor._regionElements.get('top-left')?.contains(island)).toBe(true);

        hostFixture.destroy();
        expect(replacementEditor.releaseRegionElement).toHaveBeenCalledWith('top-left');
    });

    it('should place the explanation in Apollons measured bottom-center island', async () => {
        fixture.componentRef.setInput('umlModel', classDiagram);
        fixture.componentRef.setInput('withExplanation', true);
        fixture.detectChanges();
        await fixture.whenStable();

        const editor = component['apollonEditor'] as unknown as InstanceType<typeof MockApollonEditor>;
        const bottomCenter = fixture.nativeElement.querySelector('.modeling-editor__bottom-center');

        expect(editor.getRegionElement).toHaveBeenCalledWith('bottom-center');
        expect(editor._regionElements.get('bottom-center')?.contains(bottomCenter)).toBe(true);
        expect(bottomCenter.querySelector('jhi-modeling-explanation-editor')).not.toBeNull();

        component.ngOnDestroy();
        expect(editor.releaseRegionElement).toHaveBeenCalledWith('bottom-center');
    });

    it('should update Apollon labels without remounting when the Artemis language changes', async () => {
        fixture.componentRef.setInput('umlModel', classDiagram);
        fixture.detectChanges();
        await fixture.whenStable();

        const editor = component['apollonEditor'] as unknown as InstanceType<typeof MockApollonEditor>;
        const translateService = TestBed.inject(TranslateService) as unknown as MockTranslateService;
        editor.setLabels.mockClear();

        translateService.use('de');

        expect(editor.setLabels).toHaveBeenCalledOnce();
        expect(editor.destroy).not.toHaveBeenCalled();
    });

    it('should fullscreen the portal-safe document root, disable scroll lock, and restore the editor frame on exit', async () => {
        fixture.componentRef.setInput('umlModel', classDiagram);
        fixture.componentRef.setInput('scrollLock', true);
        fixture.detectChanges();
        await fixture.whenStable();

        const editor = component['apollonEditor'] as unknown as InstanceType<typeof MockApollonEditor>;
        const frame = fixture.nativeElement.querySelector('.modeling-editor__frame') as HTMLElement;
        const originalParent = frame.parentNode;
        const originalNextSibling = frame.nextSibling;
        let fullscreenElement: Element | null = null;
        const originalFullscreenElement = Object.getOwnPropertyDescriptor(document, 'fullscreenElement');
        const originalExitFullscreen = Object.getOwnPropertyDescriptor(document, 'exitFullscreen');
        const originalRequestFullscreen = Object.getOwnPropertyDescriptor(document.documentElement, 'requestFullscreen');
        const requestFullscreen = vi.fn(async () => {
            fullscreenElement = document.documentElement;
        });
        const exitFullscreen = vi.fn(async () => {
            fullscreenElement = null;
        });
        Object.defineProperty(document.documentElement, 'requestFullscreen', { configurable: true, value: requestFullscreen });
        Object.defineProperty(document, 'fullscreenElement', { configurable: true, get: () => fullscreenElement });
        Object.defineProperty(document, 'exitFullscreen', { configurable: true, value: exitFullscreen });
        vi.spyOn(window, 'requestAnimationFrame').mockImplementation((callback) => {
            return window.setTimeout(() => callback(0), 0);
        });

        try {
            await component.toggleFullscreen();
            await fixture.whenStable();
            component['onFullscreenChange']();
            await fixture.whenStable();
            expect(component.problemStatementVisible()).toBe(false);

            fixture.componentRef.setInput('problemStatement', '## Your task');
            fixture.detectChanges();
            await fixture.whenStable();
            await vi.waitFor(() => expect(editor.fitView).toHaveBeenCalledOnce());

            expect(requestFullscreen).toHaveBeenCalledOnce();
            expect(component.fullscreenActive()).toBe(true);
            expect(frame.parentNode).toBe(document.body);
            expect(frame.classList.contains('modeling-editor__frame--fullscreen')).toBe(true);
            expect(editor.setScrollLock).toHaveBeenCalledWith(false);
            expect(component.problemStatementVisible()).toBe(true);

            await component.toggleFullscreen();
            expect(exitFullscreen).toHaveBeenCalledOnce();
            await fixture.whenStable();
            component['onFullscreenChange']();
            await fixture.whenStable();
            await vi.waitFor(() => expect(editor.fitView).toHaveBeenCalledTimes(2));

            expect(component.fullscreenActive()).toBe(false);
            expect(frame.parentNode).toBe(originalParent);
            expect(frame.nextSibling).toBe(originalNextSibling);
            expect(frame.classList.contains('modeling-editor__frame--fullscreen')).toBe(false);
            expect(editor.setScrollLock).toHaveBeenLastCalledWith(true);
            expect(component.problemStatementVisible()).toBe(false);
        } finally {
            component['restoreFullscreenPresentation']();
            if (originalFullscreenElement) {
                Object.defineProperty(document, 'fullscreenElement', originalFullscreenElement);
            } else {
                Reflect.deleteProperty(document, 'fullscreenElement');
            }
            if (originalExitFullscreen) {
                Object.defineProperty(document, 'exitFullscreen', originalExitFullscreen);
            } else {
                Reflect.deleteProperty(document, 'exitFullscreen');
            }
            if (originalRequestFullscreen) {
                Object.defineProperty(document.documentElement, 'requestFullscreen', originalRequestFullscreen);
            } else {
                Reflect.deleteProperty(document.documentElement, 'requestFullscreen');
            }
        }
    });

    it('should restore the editor and scroll-lock setting when fullscreen is rejected', async () => {
        fixture.componentRef.setInput('umlModel', classDiagram);
        fixture.componentRef.setInput('scrollLock', true);
        fixture.detectChanges();
        await fixture.whenStable();

        const editor = component['apollonEditor'] as unknown as InstanceType<typeof MockApollonEditor>;
        const frame = fixture.nativeElement.querySelector('.modeling-editor__frame') as HTMLElement;
        const originalParent = frame.parentNode;
        const originalNextSibling = frame.nextSibling;
        const originalRequestFullscreen = Object.getOwnPropertyDescriptor(document.documentElement, 'requestFullscreen');
        Object.defineProperty(document.documentElement, 'requestFullscreen', {
            configurable: true,
            value: vi.fn().mockRejectedValue(new Error('Fullscreen denied')),
        });

        try {
            await component.toggleFullscreen();

            expect(component.fullscreenActive()).toBe(false);
            expect(frame.parentNode).toBe(originalParent);
            expect(frame.nextSibling).toBe(originalNextSibling);
            expect(editor.setScrollLock).toHaveBeenNthCalledWith(1, false);
            expect(editor.setScrollLock).toHaveBeenNthCalledWith(2, true);
        } finally {
            component['restoreFullscreenPresentation']();
            if (originalRequestFullscreen) {
                Object.defineProperty(document.documentElement, 'requestFullscreen', originalRequestFullscreen);
            } else {
                Reflect.deleteProperty(document.documentElement, 'requestFullscreen');
            }
        }
    });

    it('should append the help dialog to the portal-safe document layer', async () => {
        fixture.detectChanges();

        component.openHelp();
        fixture.detectChanges();
        await fixture.whenStable();

        const overlayContainer = document.body.querySelector('.cdk-overlay-container');
        expect(overlayContainer?.querySelector('.tum-ui-dialog')).not.toBeNull();
        expect(overlayContainer?.parentElement).toBe(document.body);

        component.helpVisible.set(false);
        fixture.detectChanges();
    });

    it('should allow a containing fullscreen surface to suppress the editor fullscreen action', () => {
        fixture.componentRef.setInput('showFullscreenButton', false);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('[data-testid="modeling-editor-fullscreen"]')).toBeNull();
    });

    it.each([
        { savedStatus: { isSaving: false, isChanged: false }, state: 'saved', translationKey: 'artemisApp.modelingEditor.allSaved' },
        { savedStatus: { isSaving: false, isChanged: true }, state: 'unsaved', translationKey: 'artemisApp.modelingEditor.unsavedChanges' },
        { savedStatus: { isSaving: true, isChanged: true }, state: 'saving', translationKey: 'artemisApp.modelingEditor.saving' },
    ])('renders the $state save state', ({ savedStatus, state, translationKey }) => {
        fixture.componentRef.setInput('savedStatus', savedStatus);
        fixture.detectChanges();

        const status = fixture.debugElement.query(By.css(`.modeling-editor__status-island--${state}`));
        expect(status.query(By.css('fa-icon'))).not.toBeNull();
        expect(status.query(By.css('span')).nativeElement.getAttribute('jhiTranslate')).toBe(translationKey);
    });

    it('resynchronizes Yjs state and local awareness after reconnecting', () => {
        const collaborationUser = { id: 'student1', name: 'Student One', color: '#123456' };
        fixture.componentRef.setInput('collaborationUser', collaborationUser);
        expect(() => component.resynchronizeCollaborationAfterReconnect()).not.toThrow();

        fixture.componentRef.setInput('umlModel', classDiagram);
        fixture.componentRef.setInput('collaborationEnabled', true);
        fixture.detectChanges();

        const editor = component['apollonEditor'] as unknown as InstanceType<typeof MockApollonEditor>;
        editor.broadcastFullState.mockClear();
        editor.setLocalAwarenessUser.mockClear();
        component.resynchronizeCollaborationAfterReconnect();
        expect(editor.broadcastFullState).toHaveBeenCalledOnce();
        expect(editor.setLocalAwarenessUser).toHaveBeenCalledExactlyOnceWith(collaborationUser);
    });

    it('forwards Apollon collaboration patches', async () => {
        fixture.componentRef.setInput('umlModel', classDiagram);
        fixture.detectChanges();

        const receiver = vi.fn();
        component.onModelPatch.subscribe(receiver);

        await component.ngAfterViewInit();

        const editor = component['apollonEditor'] as any;

        expect(editor.sendBroadcastMessage).toHaveBeenCalled();

        const broadcastCallback = editor._broadcastCallback;
        expect(broadcastCallback).toBeDefined();

        const testPatch = 'base64EncodedPatchData';
        broadcastCallback?.(testPatch);
        expect(receiver).toHaveBeenCalledWith(testPatch);
    });
});
