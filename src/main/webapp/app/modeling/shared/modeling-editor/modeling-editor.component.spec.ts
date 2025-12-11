import { Course } from 'app/core/course/shared/entities/course.model';
import { By } from '@angular/platform-browser';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { Subject, of } from 'rxjs';
import { ApollonDiagram } from 'app/modeling/shared/entities/apollon-diagram.model';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ApollonEditor, Patch, UMLDiagramType, UMLModel } from '@ls1intum/apollon';
import { ModelingEditorComponent } from 'app/modeling/shared/modeling-editor/modeling-editor.component';
import * as testClassDiagram from 'test/helpers/sample/modeling/test-models/class-diagram.json';
import { cloneDeep } from 'lodash-es';
import { SimpleChange } from '@angular/core';
import { MockComponent } from 'ng-mocks';
import { ModelingExplanationEditorComponent } from 'app/modeling/shared/modeling-explanation-editor/modeling-explanation-editor.component';
import { provideHttpClient } from '@angular/common/http';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('ModelingEditorComponent', () => {
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
            declarations: [ModelingEditorComponent, MockComponent(ModelingExplanationEditorComponent)],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ModelingEditorComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('ngAfterViewInit', async () => {
        jest.spyOn(console, 'error').mockImplementation(); // prevent: findDOMNode is deprecated and will be removed in the next major release
        fixture.componentRef.setInput('umlModel', classDiagram);
        fixture.detectChanges();

        // test
        await component.ngAfterViewInit();
        const editor: ApollonEditor = component['apollonEditor'] as ApollonEditor;
        // Check that editor exists
        expect(editor).toBeDefined();
        await editor.nextRender;

        expect(Object.keys(editor.model.elements)).toEqual(Object.keys(classDiagram.elements));
    });

    it('ngOnDestroy', () => {
        fixture.componentRef.setInput('umlModel', classDiagram);
        fixture.detectChanges();
        component.ngAfterViewInit();

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
        changedModel.elements = {};
        changedModel.relationships = {};
        changedModel.interactive = { elements: {}, relationships: {} };
        changedModel.size = { height: 0, width: 0 };
        // note: using cloneDeep a default value exists, which would prevent the comparison below to pass, therefore we need to remove it here
        changedModel.default = undefined;
        // test
        await component.apollonEditor?.nextRender;
        fixture.componentRef.setInput('umlModel', changedModel);
        fixture.detectChanges();
        await component.apollonEditor?.nextRender;
        const componentModel = component['apollonEditor']!.model as UMLModel;
        expect(componentModel).toEqual(changedModel);
    });

    it('isFullScreen false', () => {
        // test
        const fullScreen = component.isFullScreen;
        expect(fullScreen).toBeFalse();
    });

    it('getCurrentModel', () => {
        fixture.componentRef.setInput('umlModel', classDiagram);
        fixture.detectChanges();
        component.ngAfterViewInit();

        // test
        // const model = component.getCurrentModel();
        // TODO: uncomment after deserialization bugfix in Apollon library, see https://github.com/ls1intum/Apollon/issues/146
        // expect(model).toEqual(testClassDiagram);
    });

    it('elementWithClass', () => {
        const model = classDiagram;
        fixture.componentRef.setInput('umlModel', classDiagram);
        fixture.detectChanges();
        component.ngAfterViewInit();

        // test
        const umlElement = component.elementWithClass('Sibling 2', model);
        expect(umlElement?.id).toBe('e0dad7e7-f67b-4e4a-8845-6c5d801ea9ca');
    });

    it('elementWithAttribute', () => {
        const model = classDiagram;
        fixture.componentRef.setInput('umlModel', classDiagram);
        fixture.detectChanges();
        component.ngAfterViewInit();

        // test
        const umlElement = component.elementWithAttribute('attribute', model);
        expect(umlElement?.id).toBe('6f572312-066b-4678-9c03-5032f3ba9be9');
    });

    it('elementWithMethod', () => {
        const model = classDiagram;
        fixture.componentRef.setInput('umlModel', classDiagram);
        fixture.detectChanges();
        component.ngAfterViewInit();

        // test
        const umlElement = component.elementWithMethod('method', model);
        expect(umlElement?.id).toBe('11aae531-3244-4d07-8d60-b6210789ffa3');
    });

    it('should not show save indicator without savedStatus set', () => {
        fixture.componentRef.setInput('savedStatus', undefined);
        fixture.componentRef.setInput('readOnly', true);

        fixture.componentRef.setInput('umlModel', classDiagram);
        fixture.detectChanges();
        component.ngAfterViewInit();

        const statusHint = fixture.debugElement.query(By.css('.status-hint'));
        expect(statusHint).toBeNull();
    });

    it('should not show save indicator in read only mode', () => {
        fixture.componentRef.setInput('savedStatus', { isSaving: false, isChanged: false });
        fixture.componentRef.setInput('readOnly', true);
        fixture.detectChanges();
        component.ngAfterViewInit();

        const statusHint = fixture.debugElement.query(By.css('.status-hint'));
        expect(statusHint).toBeNull();
    });

    it('should not show save indicator in fullscreen mode', () => {
        fixture.componentRef.setInput('savedStatus', { isSaving: false, isChanged: false });
        jest.spyOn(component, 'isFullScreen', 'get').mockReturnValue(true);
        fixture.detectChanges();
        component.ngAfterViewInit();

        const statusHint = fixture.debugElement.query(By.css('.status-hint'));
        expect(statusHint).toBeNull();
    });

    it('should show green checkmark save indicator if everything is saved', () => {
        fixture.componentRef.setInput('savedStatus', { isSaving: false, isChanged: false });
        fixture.detectChanges();
        component.ngAfterViewInit();

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
        component.ngAfterViewInit();

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
        component.ngAfterViewInit();

        const statusHint = fixture.debugElement.query(By.css('.status-hint.text-info'));
        expect(statusHint).not.toBeNull();

        const icon = statusHint.query(By.css('fa-icon'));
        expect(icon).not.toBeNull();

        const spanElement = statusHint.query(By.css('span')).nativeElement;
        expect(spanElement.getAttribute('jhiTranslate')).toBe('artemisApp.modelingEditor.saving');
    });

    it('should handle explanation input change', () => {
        const spy = jest.spyOn(component.explanation, 'set');

        const newExplanation = 'New Explanation';
        component.explanation.set(newExplanation);

        expect(spy).toHaveBeenCalledOnce();
        expect(spy).toHaveBeenCalledWith(newExplanation);
        expect(component.explanation()).toBe(newExplanation);
    });

    it('should subscribe to model change patches and emit them.', () => {
        fixture.detectChanges();

        const receiver = jest.fn();

        component.onModelPatch.subscribe(receiver);
        const mockEmitter = new Subject<Patch>();

        jest.spyOn(ApollonEditor.prototype, 'subscribeToModelChangePatches').mockImplementation((cb) => {
            mockEmitter.subscribe(cb);
            return 42;
        });
        const cleanupSpy = jest.spyOn(ApollonEditor.prototype, 'unsubscribeFromModelChangePatches').mockImplementation(() => {});

        component.ngAfterViewInit();

        mockEmitter.next([{ op: 'add', path: '/elements', value: { id: '1', type: 'class' } }]);
        expect(receiver).toHaveBeenCalledWith([{ op: 'add', path: '/elements', value: { id: '1', type: 'class' } }]);

        component.ngOnDestroy();
        expect(cleanupSpy).toHaveBeenCalledWith(42);
    });
});
