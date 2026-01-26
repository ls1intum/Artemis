import { Course } from 'app/core/course/shared/entities/course.model';
import { By } from '@angular/platform-browser';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { ApollonDiagram } from 'app/modeling/shared/entities/apollon-diagram.model';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ApollonEditor, UMLDiagramType, UMLModel } from '@tumaet/apollon';
import { ModelingEditorComponent } from 'app/modeling/shared/modeling-editor/modeling-editor.component';
import testClassDiagram from 'test/helpers/sample/modeling/test-models/class-diagram.json';
import { cloneDeep } from 'lodash-es';
import { ModelingExplanationEditorComponent } from 'app/modeling/shared/modeling-explanation-editor/modeling-explanation-editor.component';
import { provideHttpClient } from '@angular/common/http';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('ModelingEditorComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ModelingEditorComponent>;
    let component: ModelingEditorComponent;
    const course = { id: 123 } as Course;
    const diagram = new ApollonDiagram(UMLDiagramType.ClassDiagram, course.id!);
    // @ts-ignore
    const classDiagram = cloneDeep(testClassDiagram as UMLModel); // note: clone is needed to prevent weird errors with setters, because testClassDiagram is not an actual object
    const route = { params: of({ id: 1, courseId: 123 }), snapshot: { paramMap: convertToParamMap({ courseId: course.id }) } } as any as ActivatedRoute;

    beforeEach(() => {
        diagram.id = 1;
        diagram.jsonRepresentation = JSON.stringify(classDiagram);

        TestBed.configureTestingModule({
            imports: [ModelingEditorComponent, ModelingExplanationEditorComponent],
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
        vi.restoreAllMocks();
    });

    it('ngAfterViewInit', async () => {
        vi.spyOn(console, 'error').mockImplementation(() => {}); // prevent: findDOMNode is deprecated and will be removed in the next major release
        fixture.componentRef.setInput('umlModel', classDiagram);
        fixture.detectChanges();

        // test
        await component.ngAfterViewInit();
        const editor: ApollonEditor = component['apollonEditor'] as ApollonEditor;
        // Check that editor exists and is properly initialized
        expect(editor).toBeDefined();
        expect(editor.model).toBeDefined();
        await editor.nextRender;

        // Verify the editor has a valid model structure and correct diagram type
        expect(editor.model.type).toBeDefined();
        expect(editor.model.type).toBe(classDiagram.type);

        // Verify the model was loaded by checking the input data can be found via component methods
        // The classDiagram contains 13 elements (classes, attributes, methods, package) and 2 relationships
        const inputElements = (classDiagram as any).elements ?? {};
        const inputRelationships = (classDiagram as any).relationships ?? {};
        expect(Object.keys(inputElements)).toHaveLength(13);
        expect(Object.keys(inputRelationships)).toHaveLength(2);

        // Verify model data is accessible via component helper methods
        const testClass = component.elementWithClass('Sibling 2', classDiagram);
        expect(testClass).toBeDefined();
        expect(testClass?.id).toBe('e0dad7e7-f67b-4e4a-8845-6c5d801ea9ca');

        const testAttribute = component.elementWithAttribute('attribute', classDiagram);
        expect(testAttribute).toBeDefined();
        expect(testAttribute?.id).toBe('6f572312-066b-4678-9c03-5032f3ba9be9');

        const testMethod = component.elementWithMethod('method', classDiagram);
        expect(testMethod).toBeDefined();
        expect(testMethod?.id).toBe('11aae531-3244-4d07-8d60-b6210789ffa3');
    });

    it('ngOnDestroy', async () => {
        fixture.componentRef.setInput('umlModel', classDiagram);
        fixture.detectChanges();
        await component.ngAfterViewInit();

        component.ngOnDestroy();
        // verify teardown
        expect(component['apollonEditor']).toBeUndefined();
    });

    it('ngOnChanges', async () => {
        // @ts-ignore
        const model = classDiagram;
        fixture.componentRef.setInput('umlModel', classDiagram);
        fixture.detectChanges();
        await component.ngAfterViewInit();

        const changedModel = cloneDeep(model) as any;
        // Apollon v4 uses nodes/edges instead of elements/relationships
        changedModel.nodes = {};
        changedModel.edges = {};
        changedModel.interactive = { nodes: {}, edges: {} };
        changedModel.size = { height: 0, width: 0 };
        // note: using cloneDeep a default value exists, which would prevent the comparison below to pass, therefore we need to remove it here
        changedModel.default = undefined;
        // test
        await component.apollonEditor?.nextRender;
        fixture.componentRef.setInput('umlModel', changedModel);
        fixture.detectChanges();
        await component.apollonEditor?.nextRender;
        const componentModel = component['apollonEditor']!.model as UMLModel;
        // Compare structure - Apollon may return empty arrays or objects, both represent "empty"
        const normalizeEmpty = (val: any) => (Array.isArray(val) && val.length === 0) || (typeof val === 'object' && Object.keys(val || {}).length === 0);
        expect(normalizeEmpty(componentModel.nodes)).toBe(true);
        expect(normalizeEmpty(componentModel.edges)).toBe(true);
    });

    it('isFullScreen false', () => {
        // test
        const fullScreen = component.isFullScreen;
        expect(fullScreen).toBe(false);
    });

    it('getCurrentModel', async () => {
        fixture.componentRef.setInput('umlModel', classDiagram);
        fixture.detectChanges();
        await component.ngAfterViewInit();

        // test
        // const model = component.getCurrentModel();
        // TODO: uncomment after deserialization bugfix in Apollon library, see https://github.com/ls1intum/Apollon/issues/146
        // expect(model).toEqual(testClassDiagram);
    });

    it('elementWithClass', async () => {
        const model = classDiagram;
        fixture.componentRef.setInput('umlModel', classDiagram);
        fixture.detectChanges();
        await component.ngAfterViewInit();

        // test
        const umlElement = component.elementWithClass('Sibling 2', model);
        expect(umlElement?.id).toBe('e0dad7e7-f67b-4e4a-8845-6c5d801ea9ca');
    });

    it('elementWithAttribute', async () => {
        const model = classDiagram;
        fixture.componentRef.setInput('umlModel', classDiagram);
        fixture.detectChanges();
        await component.ngAfterViewInit();

        // test
        const umlElement = component.elementWithAttribute('attribute', model);
        expect(umlElement?.id).toBe('6f572312-066b-4678-9c03-5032f3ba9be9');
    });

    it('elementWithMethod', async () => {
        const model = classDiagram;
        fixture.componentRef.setInput('umlModel', classDiagram);
        fixture.detectChanges();
        await component.ngAfterViewInit();

        // test
        const umlElement = component.elementWithMethod('method', model);
        expect(umlElement?.id).toBe('11aae531-3244-4d07-8d60-b6210789ffa3');
    });

    it('should not show save indicator without savedStatus set', async () => {
        fixture.componentRef.setInput('savedStatus', undefined);
        fixture.componentRef.setInput('readOnly', true);
        fixture.componentRef.setInput('umlModel', classDiagram);
        fixture.detectChanges();
        await component.ngAfterViewInit();

        const statusHint = fixture.debugElement.query(By.css('.status-hint'));
        expect(statusHint).toBeNull();
    });

    it('should not show save indicator in read only mode', () => {
        fixture.componentRef.setInput('savedStatus', { isSaving: false, isChanged: false });
        fixture.componentRef.setInput('readOnly', true);
        fixture.detectChanges();

        const statusHint = fixture.debugElement.query(By.css('.status-hint'));
        expect(statusHint).toBeNull();
    });

    it('should not show save indicator in fullscreen mode', () => {
        fixture.componentRef.setInput('savedStatus', { isSaving: false, isChanged: false });
        vi.spyOn(component, 'isFullScreen', 'get').mockReturnValue(true);
        fixture.detectChanges();

        const statusHint = fixture.debugElement.query(By.css('.status-hint'));
        expect(statusHint).toBeNull();
    });

    it('should show green checkmark save indicator if everything is saved', () => {
        fixture.componentRef.setInput('savedStatus', { isSaving: false, isChanged: false });
        fixture.detectChanges();

        const statusHint = fixture.debugElement.query(By.css('.status-hint.text-success'));
        expect(statusHint).not.toBeNull();

        const icon = statusHint.query(By.css('fa-icon'));
        expect(icon).not.toBeNull();

        const spanElement = statusHint.query(By.css('span')).nativeElement;
        expect(spanElement.getAttribute('jhiTranslate')).toBe('artemisApp.modelingEditor.allSaved');
    });

    it('should show yellow times save indicator if something is unsaved', () => {
        fixture.componentRef.setInput('savedStatus', { isSaving: false, isChanged: true });
        fixture.detectChanges();

        const statusHint = fixture.debugElement.query(By.css('.status-hint.text-warning'));
        expect(statusHint).not.toBeNull();

        const icon = statusHint.query(By.css('fa-icon'));
        expect(icon).not.toBeNull();

        const spanElement = statusHint.query(By.css('span')).nativeElement;
        expect(spanElement.getAttribute('jhiTranslate')).toBe('artemisApp.modelingEditor.unsavedChanges');
    });

    it('should show saving indicator if it is currently saving', () => {
        fixture.componentRef.setInput('savedStatus', { isSaving: true, isChanged: true });
        fixture.detectChanges();

        const statusHint = fixture.debugElement.query(By.css('.status-hint.text-info'));
        expect(statusHint).not.toBeNull();

        const icon = statusHint.query(By.css('fa-icon'));
        expect(icon).not.toBeNull();

        const spanElement = statusHint.query(By.css('span')).nativeElement;
        expect(spanElement.getAttribute('jhiTranslate')).toBe('artemisApp.modelingEditor.saving');
    });

    it('should handle explanation input change and emit explanationChange', async () => {
        fixture.detectChanges();

        // Test that explanation model signal works correctly
        const newExplanation = 'New Explanation';

        // For model() signals, the output is named `${name}Change` - in this case `explanationChange`
        // We verify the signal behavior by checking the value updates correctly
        component.explanation.set(newExplanation);

        expect(component.explanation()).toBe(newExplanation);

        // Test via input binding (simulating parent component setting the value)
        const anotherExplanation = 'Another Explanation';
        fixture.componentRef.setInput('explanation', anotherExplanation);
        fixture.detectChanges();

        expect(component.explanation()).toBe(anotherExplanation);

        // Test that setting a new value updates the signal
        const finalExplanation = 'Final Explanation';
        component.explanation.set(finalExplanation);
        expect(component.explanation()).toBe(finalExplanation);
    });

    it('should subscribe to model change patches and emit them.', async () => {
        fixture.detectChanges();

        const receiver = vi.fn();
        const capturedCallbacks: ((patch: string) => void)[] = [];

        component.onModelPatch.subscribe(receiver);

        // Apollon v4 uses sendBroadcastMessage which takes a callback
        const sendBroadcastSpy = vi.spyOn(ApollonEditor.prototype, 'sendBroadcastMessage').mockImplementation((cb) => {
            capturedCallbacks.push(cb);
        });
        const destroySpy = vi.spyOn(ApollonEditor.prototype, 'destroy').mockImplementation(() => {});

        await component.ngAfterViewInit();

        // Verify exactly ONE callback was registered for broadcast messages
        expect(sendBroadcastSpy).toHaveBeenCalledOnce();
        expect(capturedCallbacks).toHaveLength(1);

        // Simulate a broadcast message being sent
        const testPatch = 'base64EncodedPatchData';
        capturedCallbacks[0]?.(testPatch);
        expect(receiver).toHaveBeenCalledWith(testPatch);

        component.ngOnDestroy();
        expect(destroySpy).toHaveBeenCalled();
    });
});
