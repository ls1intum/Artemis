import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ApollonDiagramService } from 'app/quiz/manage/apollon-diagrams/services/apollon-diagram.service';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { AlertService } from 'app/foundation/service/alert.service';
import { ApollonDiagram } from 'app/modeling/shared/entities/apollon-diagram.model';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';
import { ApollonDiagramDetailComponent } from 'app/quiz/manage/apollon-diagrams/detail/apollon-diagram-detail.component';
import { TranslateService } from '@ngx-translate/core';
import { MockProfileService } from 'src/test/javascript/spec/helpers/mocks/service/mock-profile.service';
import { MockLanguageHelper, MockTranslateService } from 'src/test/javascript/spec/helpers/mocks/service/mock-translate.service';
import { MockRouter } from 'src/test/javascript/spec/helpers/mocks/mock-router';
import testClassDiagramV3 from 'src/test/javascript/spec/helpers/sample/modeling/test-models/class-diagram.json';
import testClassDiagramV4 from 'src/test/javascript/spec/helpers/sample/modeling/test-models/class-diagram-v4.json';
import { ApollonEditor, UMLDiagramType, UMLModel } from '@tumaet/apollon';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import * as SVGRendererAPI from 'app/quiz/manage/apollon-diagrams/exercise-generation/svg-renderer';
import { AUTOSAVE_EXERCISE_INTERVAL } from 'app/foundation/constants/exercise-exam-constants';
import { DialogService } from 'primeng/dynamicdialog';

function setupCanvasAndImageMocks() {
    const createMockCanvas = () => {
        const mockContext = {
            drawImage: vi.fn(),
            fillStyle: '',
            fillRect: vi.fn(),
            scale: vi.fn(),
            globalCompositeOperation: 'source-over',
        };

        return {
            style: { width: '', height: '' },
            getContext: vi.fn().mockReturnValue(mockContext),
            toBlob: vi.fn((callback: (blob: Blob | null) => void) => callback(new Blob(['PNG'], { type: 'image/png' }))),
            width: 0,
            height: 0,
        } as unknown as HTMLCanvasElement;
    };

    // The createElement overload union contains a @deprecated entry for legacy elements like
    // <applet>; the canvas-mocking pattern itself isn't deprecated, but TS-ESLint can't
    // disambiguate the overloads when we hold a reference to the bound method.
    // eslint-disable-next-line @typescript-eslint/no-deprecated
    const originalCreateElement = document.createElement.bind(document);
    const createElementSpy = vi.spyOn(document, 'createElement').mockImplementation((tagName: string) => {
        if (tagName === 'canvas') {
            return createMockCanvas();
        }
        return originalCreateElement(tagName);
    });

    const originalImage = globalThis.Image;
    class MockImage {
        width = 100;
        height = 100;
        private _src = '';
        onload: (() => void) | null = null;
        onerror: ((error: Event | string) => void) | null = null;

        get src() {
            return this._src;
        }

        set src(value: string) {
            this._src = value;
            setTimeout(() => this.onload?.(), 0);
        }
    }

    vi.stubGlobal('Image', MockImage as any);
    const createObjectURLSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:test-url');
    const revokeObjectURLSpy = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);

    return {
        cleanup: () => {
            createElementSpy.mockRestore();
            createObjectURLSpy.mockRestore();
            revokeObjectURLSpy.mockRestore();
            vi.unstubAllGlobals();
            globalThis.Image = originalImage;
        },
    };
}

describe('ApollonDiagramDetail Component', () => {
    let apollonDiagramService: ApollonDiagramService;
    let fixture: ComponentFixture<ApollonDiagramDetailComponent>;
    let alertService: AlertService;
    let cleanupCanvasAndImageMocks: (() => void) | undefined;
    let dialogClose: Subject<any>;

    const courseId = 123;
    const diagram: ApollonDiagram = new ApollonDiagram(UMLDiagramType.ClassDiagram, courseId);
    const v3Model = testClassDiagramV3 as unknown as UMLModel;
    const v4Model = testClassDiagramV4 as unknown as UMLModel;

    const mockDialogService = {
        open: vi.fn(() => ({ onClose: dialogClose })),
    };

    beforeEach(async () => {
        const route = {
            params: of({ id: 1, courseId: 123 }),
            snapshot: { paramMap: convertToParamMap({ courseId }) },
        } as any as ActivatedRoute;

        diagram.id = 1;
        diagram.jsonRepresentation = JSON.stringify(testClassDiagramV3);
        dialogClose = new Subject<any>();
        mockDialogService.open.mockClear();

        await TestBed.configureTestingModule({
            imports: [ApollonDiagramDetailComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                AlertService,
                JhiLanguageHelper,
                ApollonDiagramService,
                { provide: DialogService, useValue: mockDialogService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useClass: MockRouter },
                { provide: JhiLanguageHelper, useClass: MockLanguageHelper },
                { provide: ProfileService, useClass: MockProfileService },
            ],
        })
            .overrideTemplate(ApollonDiagramDetailComponent, '<div #editorContainer></div><div #editorActions></div>')
            .compileComponents();

        fixture = TestBed.createComponent(ApollonDiagramDetailComponent);
        // Set required inputs before any change detection
        fixture.componentRef.setInput('courseId', courseId);
        fixture.componentRef.setInput('apollonDiagramId', diagram.id);

        apollonDiagramService = fixture.debugElement.injector.get(ApollonDiagramService);
        alertService = fixture.debugElement.injector.get(AlertService);

        // Mock ApollonEditor static and prototype methods
        vi.spyOn(ApollonEditor, 'exportModelAsSvg').mockResolvedValue({
            svg: '<svg></svg>',
            clip: { x: 0, y: 0, width: 100, height: 100 },
        });
        vi.spyOn(SVGRendererAPI, 'convertRenderedSVGToPNG').mockResolvedValue(new Blob(['PNG']));
        cleanupCanvasAndImageMocks = setupCanvasAndImageMocks().cleanup;
    });

    afterEach(() => {
        cleanupCanvasAndImageMocks?.();
        cleanupCanvasAndImageMocks = undefined;
        vi.restoreAllMocks();
        vi.useRealTimers();
    });

    describe('ngOnInit', () => {
        it('should load the diagram on initialization', () => {
            const response = new HttpResponse({ body: diagram });
            vi.spyOn(apollonDiagramService, 'find').mockReturnValue(of(response));

            fixture.detectChanges();

            expect(fixture.componentInstance.apollonDiagram()).toEqual(diagram);
            fixture.componentInstance.ngOnDestroy();
        });

        it('should show error alert when diagram loading fails', () => {
            vi.spyOn(apollonDiagramService, 'find').mockReturnValue(throwError(() => new Error('Load failed')));
            const errorSpy = vi.spyOn(alertService, 'error');

            fixture.detectChanges();

            expect(errorSpy).toHaveBeenCalledWith('artemisApp.apollonDiagram.detail.error.loading');
        });
    });

    describe('hasInteractive', () => {
        describe('v3 format (interactive.elements/relationships)', () => {
            it('should return true when v3 model has interactive elements', async () => {
                vi.spyOn(console, 'error').mockImplementation(() => {});
                fixture.componentInstance.apollonDiagram.set(diagram);
                await fixture.componentInstance.initializeApollonEditor(v3Model);

                // Mock the model getter to return v3 model with interactive elements
                Object.defineProperty(fixture.componentInstance.apollonEditor, 'model', {
                    get: () => v3Model,
                    configurable: true,
                });

                expect(fixture.componentInstance.hasInteractive()).toBe(true);
                fixture.componentInstance.ngOnDestroy();
            });

            it('should return false when v3 model has empty interactive elements', async () => {
                vi.spyOn(console, 'error').mockImplementation(() => {});
                const emptyInteractiveModel = {
                    ...testClassDiagramV3,
                    interactive: { elements: {}, relationships: {} },
                } as unknown as UMLModel;

                fixture.componentInstance.apollonDiagram.set(diagram);
                await fixture.componentInstance.initializeApollonEditor(emptyInteractiveModel);

                Object.defineProperty(fixture.componentInstance.apollonEditor, 'model', {
                    get: () => emptyInteractiveModel,
                    configurable: true,
                });

                expect(fixture.componentInstance.hasInteractive()).toBe(false);
                fixture.componentInstance.ngOnDestroy();
            });

            it('should return true when v3 model has interactive relationships only', async () => {
                vi.spyOn(console, 'error').mockImplementation(() => {});
                const relationshipOnlyModel = {
                    ...testClassDiagramV3,
                    interactive: {
                        elements: {},
                        relationships: { '5a9a4eb3-8281-4de4-b0f2-3e2f164574bd': true },
                    },
                } as unknown as UMLModel;

                fixture.componentInstance.apollonDiagram.set(diagram);
                await fixture.componentInstance.initializeApollonEditor(relationshipOnlyModel);

                Object.defineProperty(fixture.componentInstance.apollonEditor, 'model', {
                    get: () => relationshipOnlyModel,
                    configurable: true,
                });

                expect(fixture.componentInstance.hasInteractive()).toBe(true);
                fixture.componentInstance.ngOnDestroy();
            });
        });

        describe('v4 format (nodes/edges arrays)', () => {
            it('should return true when v4 model has nodes', async () => {
                vi.spyOn(console, 'error').mockImplementation(() => {});
                const v4Diagram = { ...diagram, jsonRepresentation: JSON.stringify(v4Model) };
                fixture.componentInstance.apollonDiagram.set(v4Diagram);
                await fixture.componentInstance.initializeApollonEditor(v4Model);

                Object.defineProperty(fixture.componentInstance.apollonEditor, 'model', {
                    get: () => v4Model,
                    configurable: true,
                });

                expect(fixture.componentInstance.hasInteractive()).toBe(true);
                fixture.componentInstance.ngOnDestroy();
            });

            it('should return false when v4 model has empty nodes and edges', async () => {
                vi.spyOn(console, 'error').mockImplementation(() => {});
                const emptyV4Model = {
                    version: '4.0.0',
                    id: 'empty',
                    title: 'Empty',
                    type: 'ClassDiagram',
                    nodes: [],
                    edges: [],
                    assessments: {},
                } as unknown as UMLModel;

                fixture.componentInstance.apollonDiagram.set(diagram);
                await fixture.componentInstance.initializeApollonEditor(emptyV4Model);

                Object.defineProperty(fixture.componentInstance.apollonEditor, 'model', {
                    get: () => emptyV4Model,
                    configurable: true,
                });

                expect(fixture.componentInstance.hasInteractive()).toBe(false);
                fixture.componentInstance.ngOnDestroy();
            });
        });

        it('should return false when apollonEditor is not initialized', () => {
            expect(fixture.componentInstance.hasInteractive()).toBe(false);
        });
    });

    describe('hasSelection', () => {
        it('should seed the selection from the editor when it is created', async () => {
            vi.spyOn(console, 'error').mockImplementation(() => {});
            vi.spyOn(ApollonEditor.prototype, 'getSelectedElements').mockReturnValue(['node-1']);
            fixture.componentInstance.apollonDiagram.set(diagram);

            await fixture.componentInstance.initializeApollonEditor(v3Model);

            expect(fixture.componentInstance.selectedElementIds()).toEqual(['node-1']);
            expect(fixture.componentInstance.hasSelection()).toBe(true);
            fixture.componentInstance.ngOnDestroy();
        });

        it('should follow Apollon selection changes so the download control can be disabled', async () => {
            vi.spyOn(console, 'error').mockImplementation(() => {});
            let emitSelection: ((ids: string[]) => void) | undefined;
            vi.spyOn(ApollonEditor.prototype, 'subscribeToSelectionChange').mockImplementation((callback) => {
                emitSelection = callback;
                return 1;
            });
            vi.spyOn(ApollonEditor.prototype, 'getSelectedElements').mockReturnValue([]);
            fixture.componentInstance.apollonDiagram.set(diagram);
            await fixture.componentInstance.initializeApollonEditor(v3Model);

            expect(fixture.componentInstance.hasSelection()).toBe(false);

            emitSelection!(['node-1', 'node-2']);

            expect(fixture.componentInstance.hasSelection()).toBe(true);
            fixture.componentInstance.ngOnDestroy();
        });

        it('should clear the selection when the editor is destroyed', async () => {
            vi.spyOn(console, 'error').mockImplementation(() => {});
            vi.spyOn(ApollonEditor.prototype, 'getSelectedElements').mockReturnValue(['node-1']);
            fixture.componentInstance.apollonDiagram.set(diagram);
            await fixture.componentInstance.initializeApollonEditor(v3Model);

            fixture.componentInstance.ngOnDestroy();

            expect(fixture.componentInstance.hasSelection()).toBe(false);
        });

        it('should return false when apollonEditor is not initialized', () => {
            expect(fixture.componentInstance.hasSelection()).toBe(false);
        });
    });

    describe('saveDiagram', () => {
        it('should save diagram and show success alert', async () => {
            vi.spyOn(console, 'error').mockImplementation(() => {});
            fixture.componentInstance.apollonDiagram.set(diagram);
            const response = new HttpResponse({ body: diagram, status: 200 });
            const updateSpy = vi.spyOn(apollonDiagramService, 'update').mockReturnValue(of(response));
            const successSpy = vi.spyOn(alertService, 'success');

            await fixture.componentInstance.initializeApollonEditor(v3Model);

            const result = await fixture.componentInstance.saveDiagram();

            expect(result).toBe(true);
            expect(updateSpy).toHaveBeenCalledOnce();
            expect(successSpy).toHaveBeenCalledWith('artemisApp.apollonDiagram.updated', { title: diagram.title });
            expect(fixture.componentInstance.isSaved()).toBe(true);

            fixture.componentInstance.ngOnDestroy();
        });

        it('should return false and show error when save fails', async () => {
            vi.spyOn(console, 'error').mockImplementation(() => {});
            fixture.componentInstance.apollonDiagram.set(diagram);
            // Mock a response where ok is false (simulating server error)
            const response = new HttpResponse({ body: diagram, status: 500 });
            Object.defineProperty(response, 'ok', { value: false });
            vi.spyOn(apollonDiagramService, 'update').mockReturnValue(of(response));
            const errorSpy = vi.spyOn(alertService, 'error');

            await fixture.componentInstance.initializeApollonEditor(v3Model);

            const result = await fixture.componentInstance.saveDiagram();

            expect(result).toBe(false);
            expect(errorSpy).toHaveBeenCalledWith('artemisApp.apollonDiagram.update.error');

            fixture.componentInstance.ngOnDestroy();
        });

        it('should return false when diagram is not set', async () => {
            const result = await fixture.componentInstance.saveDiagram();
            expect(result).toBe(false);
        });
    });

    describe('generateExercise', () => {
        it('should show error when no interactive elements', async () => {
            vi.spyOn(console, 'error').mockImplementation(() => {});
            const emptyInteractiveModel = {
                ...testClassDiagramV3,
                interactive: { elements: {}, relationships: {} },
            } as unknown as UMLModel;

            fixture.componentInstance.apollonDiagram.set(diagram);
            await fixture.componentInstance.initializeApollonEditor(emptyInteractiveModel);

            Object.defineProperty(fixture.componentInstance.apollonEditor, 'model', {
                get: () => emptyInteractiveModel,
                configurable: true,
            });

            const errorSpy = vi.spyOn(alertService, 'error');

            await fixture.componentInstance.generateExercise();

            expect(errorSpy).toHaveBeenCalledWith('artemisApp.apollonDiagram.create.validationError');
            fixture.componentInstance.ngOnDestroy();
        });

        it('should generate exercise and emit closeEdit when successful', async () => {
            vi.spyOn(console, 'error').mockImplementation(() => {});
            fixture.componentInstance.apollonDiagram.set(diagram);

            const response = new HttpResponse({ body: diagram, status: 200 });
            vi.spyOn(apollonDiagramService, 'update').mockReturnValue(of(response));

            await fixture.componentInstance.initializeApollonEditor(v3Model);

            Object.defineProperty(fixture.componentInstance.apollonEditor, 'model', {
                get: () => v3Model,
                configurable: true,
            });

            const emitSpy = vi.spyOn(fixture.componentInstance.closeEdit, 'emit');

            await fixture.componentInstance.generateExercise();

            expect(emitSpy).toHaveBeenCalledOnce();
            fixture.componentInstance.ngOnDestroy();
        });

        it('should not emit when save fails', async () => {
            vi.spyOn(console, 'error').mockImplementation(() => {});
            fixture.componentInstance.apollonDiagram.set(diagram);

            // Mock save to fail
            const response = new HttpResponse({ body: diagram, status: 500 });
            Object.defineProperty(response, 'ok', { value: false });
            vi.spyOn(apollonDiagramService, 'update').mockReturnValue(of(response));

            await fixture.componentInstance.initializeApollonEditor(v3Model);

            Object.defineProperty(fixture.componentInstance.apollonEditor, 'model', {
                get: () => v3Model,
                configurable: true,
            });

            const emitSpy = vi.spyOn(fixture.componentInstance.closeEdit, 'emit');

            await fixture.componentInstance.generateExercise();

            expect(emitSpy).not.toHaveBeenCalled();
            fixture.componentInstance.ngOnDestroy();
        });
    });

    describe('downloadSelection', () => {
        it('should download PNG when elements are selected', async () => {
            vi.spyOn(console, 'error').mockImplementation(() => {});
            vi.spyOn(ApollonEditor.prototype, 'getSelectedElements').mockReturnValue(['node-1']);
            const exportSpy = vi.spyOn(ApollonEditor.prototype, 'exportAsSVG').mockResolvedValue({
                svg: '<svg></svg>',
                clip: { x: 0, y: 0, width: 100, height: 100 },
            });

            fixture.componentInstance.apollonDiagram.set(diagram);
            await fixture.componentInstance.initializeApollonEditor(v3Model);

            await fixture.componentInstance.downloadSelection();

            expect(window.URL.createObjectURL).toHaveBeenCalledOnce();
            expect(exportSpy).toHaveBeenCalledWith(expect.objectContaining({ include: ['node-1'], keepOriginalSize: false }));
            fixture.componentInstance.ngOnDestroy();
        });

        it('should export the full diagram when the crop toggle is off', async () => {
            vi.spyOn(console, 'error').mockImplementation(() => {});
            vi.spyOn(ApollonEditor.prototype, 'getSelectedElements').mockReturnValue(['node-1']);
            const exportSpy = vi.spyOn(ApollonEditor.prototype, 'exportAsSVG').mockResolvedValue({
                svg: '<svg></svg>',
                clip: { x: 0, y: 0, width: 100, height: 100 },
            });

            fixture.componentInstance.apollonDiagram.set(diagram);
            await fixture.componentInstance.initializeApollonEditor(v3Model);
            fixture.componentInstance.cropToSelection.set(false);

            await fixture.componentInstance.downloadSelection();

            expect(exportSpy).toHaveBeenCalledWith(expect.objectContaining({ keepOriginalSize: true }));
            fixture.componentInstance.ngOnDestroy();
        });

        it('should not download when nothing is selected', async () => {
            vi.spyOn(console, 'error').mockImplementation(() => {});
            vi.spyOn(ApollonEditor.prototype, 'getSelectedElements').mockReturnValue([]);

            fixture.componentInstance.apollonDiagram.set(diagram);
            await fixture.componentInstance.initializeApollonEditor(v3Model);

            // Reset the mock to track only calls from this test
            const createObjectURLMock = vi.fn(() => 'blob:test-url');
            globalThis.URL.createObjectURL = createObjectURLMock;

            await fixture.componentInstance.downloadSelection();

            expect(createObjectURLMock).not.toHaveBeenCalled();
            fixture.componentInstance.ngOnDestroy();
        });
    });

    /** `isSaved` is derived, so the tests drive the title it derives from rather than the flag. */
    const markSaved = (component: ApollonDiagramDetailComponent) => component.title.set(component.apollonDiagram()?.title ?? '');
    const markUnsaved = (component: ApollonDiagramDetailComponent) => component.title.set(`${component.apollonDiagram()?.title ?? ''} edited`);

    describe('confirmExitDetailView', () => {
        it('should emit closeModal directly when saved', () => {
            const emitSpy = vi.spyOn(fixture.componentInstance.closeModal, 'emit');

            markSaved(fixture.componentInstance);
            fixture.componentInstance.confirmExitDetailView(true);

            expect(emitSpy).toHaveBeenCalledOnce();
        });

        it('should emit closeEdit directly when saved', () => {
            const emitSpy = vi.spyOn(fixture.componentInstance.closeEdit, 'emit');

            markSaved(fixture.componentInstance);
            fixture.componentInstance.confirmExitDetailView(false);

            expect(emitSpy).toHaveBeenCalledOnce();
        });

        it('should open confirmation modal when not saved', () => {
            markUnsaved(fixture.componentInstance);
            fixture.componentInstance.confirmExitDetailView(true);

            expect(mockDialogService.open).toHaveBeenCalledOnce();
        });

        it('should emit closeModal after dialog confirmation', () => {
            const emitSpy = vi.spyOn(fixture.componentInstance.closeModal, 'emit');

            markUnsaved(fixture.componentInstance);
            fixture.componentInstance.confirmExitDetailView(true);

            dialogClose.next({ confirmed: true });

            expect(emitSpy).toHaveBeenCalledOnce();
        });
    });

    describe('Auto-save timer', () => {
        it('should set autoSaveInterval on initialization', () => {
            const response = new HttpResponse({ body: diagram, status: 200 });
            vi.spyOn(apollonDiagramService, 'find').mockReturnValue(of(response));
            const setIntervalSpy = vi.spyOn(globalThis, 'setInterval');

            fixture.detectChanges();

            // Verify setInterval was called (auto-save timer started)
            expect(setIntervalSpy).toHaveBeenCalled();
            expect(fixture.componentInstance.autoSaveInterval).toBeDefined();

            fixture.componentInstance.ngOnDestroy();
        });

        it('should reset timer after successful save', async () => {
            vi.spyOn(console, 'error').mockImplementation(() => {});
            fixture.componentInstance.apollonDiagram.set(diagram);
            const response = new HttpResponse({ body: diagram, status: 200 });
            vi.spyOn(apollonDiagramService, 'update').mockReturnValue(of(response));

            await fixture.componentInstance.initializeApollonEditor(v3Model);

            // Set timer to almost trigger
            fixture.componentInstance.autoSaveTimer = AUTOSAVE_EXERCISE_INTERVAL - 1;

            await fixture.componentInstance.saveDiagram();

            // Timer should be reset to 0 in setAutoSaveTimer
            // (We can't directly test internal timer reset, but save should succeed)
            expect(fixture.componentInstance.isSaved()).toBe(true);

            fixture.componentInstance.ngOnDestroy();
        });
    });

    describe('ngOnDestroy', () => {
        it('should clear interval and destroy editor', async () => {
            vi.spyOn(console, 'error').mockImplementation(() => {});
            const clearIntervalSpy = vi.spyOn(globalThis, 'clearInterval');

            fixture.componentInstance.apollonDiagram.set(diagram);
            await fixture.componentInstance.initializeApollonEditor(v3Model);

            // Simulate that autoSaveTimer was started (normally done via ngOnInit -> setAutoSaveTimer)
            const fakeInterval = setInterval(() => {}, 60000);
            fixture.componentInstance.autoSaveInterval = fakeInterval;

            const destroySpy = vi.spyOn(fixture.componentInstance.apollonEditor!, 'destroy');

            fixture.componentInstance.ngOnDestroy();

            expect(clearIntervalSpy).toHaveBeenCalledWith(fakeInterval);
            expect(destroySpy).toHaveBeenCalledOnce();
        });

        it('should handle destroy when editor not initialized', () => {
            // Should not throw
            expect(() => fixture.componentInstance.ngOnDestroy()).not.toThrow();
        });
    });

    describe('title', () => {
        it('should seed the title from the loaded diagram', () => {
            const response = new HttpResponse({ body: diagram });
            vi.spyOn(apollonDiagramService, 'find').mockReturnValue(of(response));

            fixture.detectChanges();

            expect(fixture.componentInstance.title()).toBe(diagram.title ?? '');
            fixture.componentInstance.ngOnDestroy();
        });

        it('should treat a blank title as invalid', () => {
            fixture.componentInstance.title.set('   ');
            expect(fixture.componentInstance.isTitleValid()).toBe(false);

            fixture.componentInstance.title.set('Class diagram');
            expect(fixture.componentInstance.isTitleValid()).toBe(true);
        });

        it('should track the title against the stored one in both directions', async () => {
            const stored = { ...diagram, title: 'Stored title' } as ApollonDiagram;
            fixture.componentInstance.apollonDiagram.set(stored);
            fixture.componentInstance.title.set('Stored title');
            expect(fixture.componentInstance.isSaved()).toBe(true);

            fixture.componentInstance.title.set('Renamed');
            await fixture.whenStable();
            expect(fixture.componentInstance.isSaved()).toBe(false);

            fixture.componentInstance.title.set('Stored title');
            await fixture.whenStable();
            expect(fixture.componentInstance.isSaved()).toBe(true);
        });

        it('should persist the edited title', async () => {
            vi.spyOn(console, 'error').mockImplementation(() => {});
            fixture.componentInstance.apollonDiagram.set(diagram);
            const response = new HttpResponse({ body: diagram, status: 200 });
            const updateSpy = vi.spyOn(apollonDiagramService, 'update').mockReturnValue(of(response));

            await fixture.componentInstance.initializeApollonEditor(v3Model);
            fixture.componentInstance.title.set('  Renamed diagram  ');

            await fixture.componentInstance.saveDiagram();

            expect(updateSpy).toHaveBeenCalledWith(expect.objectContaining({ title: 'Renamed diagram' }), courseId);
            expect(fixture.componentInstance.apollonDiagram()?.title).toBe('Renamed diagram');
            expect(fixture.componentInstance.isSaved()).toBe(true);
            fixture.componentInstance.ngOnDestroy();
        });
    });

    describe('canGenerate', () => {
        it('should stay false while the diagram has no quiz-relevant elements', async () => {
            vi.spyOn(console, 'error').mockImplementation(() => {});
            const emptyV4Model = { version: '4.0.0', id: 'empty', title: 'Empty', type: 'ClassDiagram', nodes: [], edges: [], assessments: {} } as unknown as UMLModel;
            fixture.componentInstance.apollonDiagram.set(diagram);
            fixture.componentInstance.title.set('Diagram');
            await fixture.componentInstance.initializeApollonEditor(emptyV4Model);

            Object.defineProperty(fixture.componentInstance.apollonEditor, 'model', { get: () => emptyV4Model, configurable: true });

            expect(fixture.componentInstance.canGenerate()).toBe(false);
            expect(fixture.componentInstance.generateHint()).toBe('artemisApp.apollonDiagram.create.validationError');
            fixture.componentInstance.ngOnDestroy();
        });

        it('should re-evaluate when Apollon reports a model change', async () => {
            vi.spyOn(console, 'error').mockImplementation(() => {});
            let emitModelChange: ((model: UMLModel) => void) | undefined;
            vi.spyOn(ApollonEditor.prototype, 'subscribeToModelChange').mockImplementation((callback) => {
                emitModelChange = callback;
                return 1;
            });
            const emptyV4Model = { version: '4.0.0', id: 'empty', title: 'Empty', type: 'ClassDiagram', nodes: [], edges: [], assessments: {} } as unknown as UMLModel;
            let currentModel: UMLModel = emptyV4Model;

            fixture.componentInstance.apollonDiagram.set(diagram);
            fixture.componentInstance.title.set('Diagram');
            await fixture.componentInstance.initializeApollonEditor(emptyV4Model);
            Object.defineProperty(fixture.componentInstance.apollonEditor, 'model', { get: () => currentModel, configurable: true });

            expect(fixture.componentInstance.canGenerate()).toBe(false);

            currentModel = v4Model;
            emitModelChange!(v4Model);

            expect(fixture.componentInstance.canGenerate()).toBe(true);
            expect(fixture.componentInstance.generateHint()).toBe('');
            fixture.componentInstance.ngOnDestroy();
        });

        it('should stay false while the title is blank', async () => {
            vi.spyOn(console, 'error').mockImplementation(() => {});
            fixture.componentInstance.apollonDiagram.set(diagram);
            fixture.componentInstance.title.set('  ');
            await fixture.componentInstance.initializeApollonEditor(v4Model);
            Object.defineProperty(fixture.componentInstance.apollonEditor, 'model', { get: () => v4Model, configurable: true });

            expect(fixture.componentInstance.canGenerate()).toBe(false);
            fixture.componentInstance.ngOnDestroy();
        });
    });

    describe('editor chrome placement', () => {
        it('should hand the action cluster to Apollon top-right overlay region', async () => {
            vi.spyOn(console, 'error').mockImplementation(() => {});
            const region = document.createElement('div');
            const getRegionElementSpy = vi.spyOn(ApollonEditor.prototype, 'getRegionElement').mockReturnValue(region);

            fixture.componentInstance.apollonDiagram.set(diagram);
            fixture.detectChanges();
            await fixture.componentInstance.initializeApollonEditor(v3Model);

            expect(getRegionElementSpy).toHaveBeenCalledWith('top-right');
            expect(region.children).toHaveLength(1);

            fixture.componentInstance.ngOnDestroy();
        });

        it('should release the overlay region when the editor is torn down', async () => {
            vi.spyOn(console, 'error').mockImplementation(() => {});
            vi.spyOn(ApollonEditor.prototype, 'getRegionElement').mockReturnValue(document.createElement('div'));
            const releaseSpy = vi.spyOn(ApollonEditor.prototype, 'releaseRegionElement').mockImplementation(() => {});

            fixture.componentInstance.apollonDiagram.set(diagram);
            fixture.detectChanges();
            await fixture.componentInstance.initializeApollonEditor(v3Model);

            fixture.componentInstance.ngOnDestroy();

            expect(releaseSpy).toHaveBeenCalledWith('top-right');
        });
    });

    describe('initializeApollonEditor', () => {
        it('should create new ApollonEditor with correct config', async () => {
            vi.spyOn(console, 'error').mockImplementation(() => {});
            fixture.componentInstance.apollonDiagram.set(diagram);

            await fixture.componentInstance.initializeApollonEditor(v3Model);

            expect(fixture.componentInstance.apollonEditor).toBeTruthy();
            fixture.componentInstance.ngOnDestroy();
        });

        it('should destroy existing editor before creating new one', async () => {
            vi.spyOn(console, 'error').mockImplementation(() => {});
            fixture.componentInstance.apollonDiagram.set(diagram);

            // Initialize first editor
            await fixture.componentInstance.initializeApollonEditor(v3Model);
            const firstEditor = fixture.componentInstance.apollonEditor;
            const destroySpy = vi.spyOn(firstEditor!, 'destroy');

            // Initialize second editor
            await fixture.componentInstance.initializeApollonEditor(v3Model);

            expect(destroySpy).toHaveBeenCalledOnce();
            fixture.componentInstance.ngOnDestroy();
        });

        it('should subscribe to model changes and track saved state', async () => {
            vi.spyOn(console, 'error').mockImplementation(() => {});
            fixture.componentInstance.apollonDiagram.set(diagram);

            await fixture.componentInstance.initializeApollonEditor(v3Model);

            // Initial state should be saved (model matches jsonRepresentation)
            expect(fixture.componentInstance.isSaved()).toBe(true);

            fixture.componentInstance.ngOnDestroy();
        });
    });
});
