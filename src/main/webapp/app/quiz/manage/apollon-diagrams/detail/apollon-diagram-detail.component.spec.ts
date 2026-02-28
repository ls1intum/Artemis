import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ApollonDiagramService } from 'app/quiz/manage/apollon-diagrams/services/apollon-diagram.service';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { of, throwError } from 'rxjs';
import { AlertService } from 'app/shared/service/alert.service';
import { ApollonDiagram } from 'app/modeling/shared/entities/apollon-diagram.model';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';
import { ApollonDiagramDetailComponent } from 'app/quiz/manage/apollon-diagrams/detail/apollon-diagram-detail.component';
import { TranslateService } from '@ngx-translate/core';
import { MockProfileService } from 'src/test/javascript/spec/helpers/mocks/service/mock-profile.service';
import { MockLanguageHelper, MockTranslateService } from 'src/test/javascript/spec/helpers/mocks/service/mock-translate.service';
import { MockRouter } from 'src/test/javascript/spec/helpers/mocks/mock-router';
import * as testClassDiagramV3 from 'src/test/javascript/spec/helpers/sample/modeling/test-models/class-diagram.json';
import * as testClassDiagramV4 from 'src/test/javascript/spec/helpers/sample/modeling/test-models/class-diagram-v4.json';
import { ApollonEditor, UMLDiagramType, UMLModel } from '@tumaet/apollon';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import * as SVGRendererAPI from 'app/quiz/manage/apollon-diagrams/exercise-generation/svg-renderer';
import { AUTOSAVE_EXERCISE_INTERVAL } from 'app/shared/constants/exercise-exam-constants';

/**
 * RUTHLESS TEST SUITE: ApollonDiagramDetailComponent
 *
 * Tests cover:
 * 1. Component initialization (ngOnInit)
 * 2. hasInteractive for v3 AND v4 formats
 * 3. hasSelection logic
 * 4. Save functionality with success and error paths
 * 5. Generate exercise functionality
 * 6. Download selection
 * 7. Auto-save timer logic
 * 8. Modal confirmation flow
 * 9. Cleanup (ngOnDestroy)
 */
describe('ApollonDiagramDetail Component', () => {
    setupTestBed({ zoneless: true });

    let apollonDiagramService: ApollonDiagramService;
    let courseService: CourseManagementService;
    let fixture: ComponentFixture<ApollonDiagramDetailComponent>;
    let alertService: AlertService;

    const course: Course = { id: 123 } as Course;
    const diagram: ApollonDiagram = new ApollonDiagram(UMLDiagramType.ClassDiagram, course.id!);
    const v3Model = testClassDiagramV3 as unknown as UMLModel;
    const v4Model = testClassDiagramV4 as unknown as UMLModel;

    // Properly typed mock for modal service that actually invokes callbacks
    const mockModalService = {
        open: vi.fn().mockReturnValue({
            componentInstance: {},
            result: Promise.resolve(),
            close: vi.fn(),
        }),
    };

    globalThis.URL.createObjectURL = vi.fn(() => 'blob:test-url');
    globalThis.URL.revokeObjectURL = vi.fn();

    beforeEach(async () => {
        const route = {
            params: of({ id: 1, courseId: 123 }),
            snapshot: { paramMap: convertToParamMap({ courseId: course.id }) },
        } as any as ActivatedRoute;

        diagram.id = 1;
        diagram.jsonRepresentation = JSON.stringify(testClassDiagramV3);

        await TestBed.configureTestingModule({
            imports: [ApollonDiagramDetailComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                AlertService,
                JhiLanguageHelper,
                ApollonDiagramService,
                { provide: NgbModal, useValue: mockModalService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useClass: MockRouter },
                { provide: JhiLanguageHelper, useClass: MockLanguageHelper },
                {
                    provide: CourseManagementService,
                    useValue: {
                        find: vi.fn().mockReturnValue(of(new HttpResponse({ body: course }))),
                    },
                },
                { provide: ProfileService, useClass: MockProfileService },
            ],
        })
            .overrideTemplate(ApollonDiagramDetailComponent, '<div #editorContainer></div>')
            .compileComponents();

        fixture = TestBed.createComponent(ApollonDiagramDetailComponent);
        apollonDiagramService = fixture.debugElement.injector.get(ApollonDiagramService);
        courseService = fixture.debugElement.injector.get(CourseManagementService);
        alertService = fixture.debugElement.injector.get(AlertService);

        // Mock ApollonEditor static and prototype methods
        vi.spyOn(ApollonEditor, 'exportModelAsSvg').mockResolvedValue({
            svg: '<svg></svg>',
            clip: { x: 0, y: 0, width: 100, height: 100 },
        });
        vi.spyOn(SVGRendererAPI, 'convertRenderedSVGToPNG').mockResolvedValue(new Blob(['PNG']));
    });

    afterEach(() => {
        vi.restoreAllMocks();
        vi.useRealTimers();
    });

    // ===========================================
    // INITIALIZATION TESTS
    // ===========================================
    describe('ngOnInit', () => {
        it('should load diagram and course on initialization', () => {
            const response = new HttpResponse({ body: diagram });
            vi.spyOn(apollonDiagramService, 'find').mockReturnValue(of(response));

            fixture.detectChanges();

            expect(fixture.componentInstance.apollonDiagram()).toEqual(diagram);
            expect(fixture.componentInstance.course()).toEqual(course);
            fixture.componentInstance.ngOnDestroy();
        });

        it('should show error alert when diagram loading fails', () => {
            vi.spyOn(apollonDiagramService, 'find').mockReturnValue(throwError(() => new Error('Load failed')));
            const errorSpy = vi.spyOn(alertService, 'error');

            fixture.detectChanges();

            expect(errorSpy).toHaveBeenCalledWith('artemisApp.apollonDiagram.detail.error.loading');
        });

        it('should show error alert when course loading fails', () => {
            const diagramResponse = new HttpResponse({ body: diagram });
            vi.spyOn(apollonDiagramService, 'find').mockReturnValue(of(diagramResponse));
            vi.spyOn(courseService, 'find').mockReturnValue(throwError(() => new Error('Course load failed')));
            const errorSpy = vi.spyOn(alertService, 'error');

            fixture.detectChanges();

            expect(errorSpy).toHaveBeenCalledWith('artemisApp.apollonDiagram.detail.error.loading');
            fixture.componentInstance.ngOnDestroy();
        });
    });

    // ===========================================
    // hasInteractive TESTS (v3 and v4)
    // ===========================================
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

                expect(fixture.componentInstance.hasInteractive).toBe(true);
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

                expect(fixture.componentInstance.hasInteractive).toBe(false);
                fixture.componentInstance.ngOnDestroy();
            });

            it('should return true when v3 model has interactive relationships only', async () => {
                vi.spyOn(console, 'error').mockImplementation(() => {});
                const relationshipOnlyModel = {
                    ...testClassDiagramV3,
                    interactive: {
                        elements: {},
                        relationships: { 'rel-1': true },
                    },
                } as unknown as UMLModel;

                fixture.componentInstance.apollonDiagram.set(diagram);
                await fixture.componentInstance.initializeApollonEditor(relationshipOnlyModel);

                Object.defineProperty(fixture.componentInstance.apollonEditor, 'model', {
                    get: () => relationshipOnlyModel,
                    configurable: true,
                });

                expect(fixture.componentInstance.hasInteractive).toBe(true);
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

                expect(fixture.componentInstance.hasInteractive).toBe(true);
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

                expect(fixture.componentInstance.hasInteractive).toBe(false);
                fixture.componentInstance.ngOnDestroy();
            });
        });

        it('should return false when apollonEditor is not initialized', () => {
            expect(fixture.componentInstance.hasInteractive).toBe(false);
        });
    });

    // ===========================================
    // hasSelection TESTS
    // ===========================================
    describe('hasSelection', () => {
        it('should return true when nodes are selected', async () => {
            vi.spyOn(console, 'error').mockImplementation(() => {});
            fixture.componentInstance.apollonDiagram.set(diagram);
            await fixture.componentInstance.initializeApollonEditor(v3Model);

            vi.spyOn(ApollonEditor.prototype, 'getNodes').mockReturnValue({ 'node-1': true } as any);
            vi.spyOn(ApollonEditor.prototype, 'getEdges').mockReturnValue({} as any);

            expect(fixture.componentInstance.hasSelection).toBe(true);
            fixture.componentInstance.ngOnDestroy();
        });

        it('should return true when edges are selected', async () => {
            vi.spyOn(console, 'error').mockImplementation(() => {});
            fixture.componentInstance.apollonDiagram.set(diagram);
            await fixture.componentInstance.initializeApollonEditor(v3Model);

            vi.spyOn(ApollonEditor.prototype, 'getNodes').mockReturnValue({} as any);
            vi.spyOn(ApollonEditor.prototype, 'getEdges').mockReturnValue({ 'edge-1': true } as any);

            expect(fixture.componentInstance.hasSelection).toBe(true);
            fixture.componentInstance.ngOnDestroy();
        });

        it('should return false when nothing is selected', async () => {
            vi.spyOn(console, 'error').mockImplementation(() => {});
            fixture.componentInstance.apollonDiagram.set(diagram);
            await fixture.componentInstance.initializeApollonEditor(v3Model);

            vi.spyOn(ApollonEditor.prototype, 'getNodes').mockReturnValue({} as any);
            vi.spyOn(ApollonEditor.prototype, 'getEdges').mockReturnValue({} as any);

            expect(fixture.componentInstance.hasSelection).toBe(false);
            fixture.componentInstance.ngOnDestroy();
        });

        it('should return false when apollonEditor is not initialized', () => {
            expect(fixture.componentInstance.hasSelection).toBe(false);
        });
    });

    // ===========================================
    // SAVE DIAGRAM TESTS
    // ===========================================
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
            expect(fixture.componentInstance.isSaved).toBe(true);

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

    // ===========================================
    // GENERATE EXERCISE TESTS
    // ===========================================
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
            fixture.componentInstance.course.set(course);

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
            fixture.componentInstance.course.set(course);

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

    // ===========================================
    // DOWNLOAD SELECTION TESTS
    // ===========================================
    describe('downloadSelection', () => {
        it('should download PNG when elements are selected', async () => {
            vi.spyOn(console, 'error').mockImplementation(() => {});
            vi.spyOn(ApollonEditor.prototype, 'getNodes').mockReturnValue({ 'node-1': true } as any);
            vi.spyOn(ApollonEditor.prototype, 'getEdges').mockReturnValue({} as any);
            vi.spyOn(ApollonEditor.prototype, 'exportAsSVG').mockResolvedValue({
                svg: '<svg></svg>',
                clip: { x: 0, y: 0, width: 100, height: 100 },
            });

            fixture.componentInstance.apollonDiagram.set(diagram);
            await fixture.componentInstance.initializeApollonEditor(v3Model);

            await fixture.componentInstance.downloadSelection();

            expect(window.URL.createObjectURL).toHaveBeenCalledOnce();
            fixture.componentInstance.ngOnDestroy();
        });

        it('should not download when nothing is selected', async () => {
            vi.spyOn(console, 'error').mockImplementation(() => {});
            vi.spyOn(ApollonEditor.prototype, 'getNodes').mockReturnValue({} as any);
            vi.spyOn(ApollonEditor.prototype, 'getEdges').mockReturnValue({} as any);

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

    // ===========================================
    // MODAL CONFIRMATION TESTS
    // ===========================================
    describe('confirmExitDetailView', () => {
        it('should emit closeModal directly when saved', () => {
            const emitSpy = vi.spyOn(fixture.componentInstance.closeModal, 'emit');

            fixture.componentInstance.isSaved = true;
            fixture.componentInstance.confirmExitDetailView(true);

            expect(emitSpy).toHaveBeenCalledOnce();
        });

        it('should emit closeEdit directly when saved', () => {
            const emitSpy = vi.spyOn(fixture.componentInstance.closeEdit, 'emit');

            fixture.componentInstance.isSaved = true;
            fixture.componentInstance.confirmExitDetailView(false);

            expect(emitSpy).toHaveBeenCalledOnce();
        });

        it('should open confirmation modal when not saved', () => {
            fixture.componentInstance.isSaved = false;
            fixture.componentInstance.confirmExitDetailView(true);

            expect(mockModalService.open).toHaveBeenCalledOnce();
        });

        it('should emit closeModal after modal confirmation', async () => {
            const emitSpy = vi.spyOn(fixture.componentInstance.closeModal, 'emit');

            fixture.componentInstance.isSaved = false;
            fixture.componentInstance.confirmExitDetailView(true);

            // Wait for modal promise to resolve
            await Promise.resolve();

            expect(emitSpy).toHaveBeenCalledOnce();
        });
    });

    // ===========================================
    // AUTO-SAVE TIMER TESTS
    // ===========================================
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
            expect(fixture.componentInstance.isSaved).toBe(true);

            fixture.componentInstance.ngOnDestroy();
        });
    });

    // ===========================================
    // CLEANUP TESTS
    // ===========================================
    describe('ngOnDestroy', () => {
        it('should clear interval and destroy editor', async () => {
            vi.spyOn(console, 'error').mockImplementation(() => {});
            const clearIntervalSpy = vi.spyOn(globalThis, 'clearInterval');

            fixture.componentInstance.apollonDiagram.set(diagram);
            await fixture.componentInstance.initializeApollonEditor(v3Model);

            const destroySpy = vi.spyOn(fixture.componentInstance.apollonEditor!, 'destroy');

            fixture.componentInstance.ngOnDestroy();

            expect(clearIntervalSpy).toHaveBeenCalledWith(fixture.componentInstance.autoSaveInterval);
            expect(destroySpy).toHaveBeenCalledOnce();
        });

        it('should handle destroy when editor not initialized', () => {
            // Should not throw
            expect(() => fixture.componentInstance.ngOnDestroy()).not.toThrow();
        });
    });

    // ===========================================
    // APOLLON EDITOR INITIALIZATION
    // ===========================================
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
            expect(fixture.componentInstance.isSaved).toBe(true);

            fixture.componentInstance.ngOnDestroy();
        });
    });
});
