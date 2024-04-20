import { Course } from 'app/entities/course.model';
import { By } from '@angular/platform-browser';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { Subject, of } from 'rxjs';
import { ApollonDiagram } from 'app/entities/apollon-diagram.model';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { Patch, UMLDiagramType, UMLModel } from '@ls1intum/apollon';
import { Text } from '@ls1intum/apollon/lib/es5/utils/svg/text';
import { ModelingEditorComponent } from 'app/exercises/modeling/shared/modeling-editor.component';
import * as testClassDiagram from '../../util/modeling/test-models/class-diagram.json';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { ArtemisTestModule } from '../../test.module';
import { cloneDeep } from 'lodash-es';
import { SimpleChange } from '@angular/core';
import { MockComponent, MockProvider } from 'ng-mocks';
import { ModelingExplanationEditorComponent } from 'app/exercises/modeling/shared/modeling-explanation-editor.component';
import { ApollonEditor } from '@ls1intum/apollon';
import { associationUML, personUML, studentUML } from 'app/guided-tour/guided-tour-task.model';

// has to be overridden, because jsdom does not provide a getBBox() function for SVGTextElements
Text.size = () => {
    return { width: 0, height: 0 };
};

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
            imports: [HttpClientTestingModule, ArtemisTestModule],
            declarations: [ModelingEditorComponent, MockComponent(ModelingExplanationEditorComponent)],
            providers: [MockProvider(GuidedTourService), { provide: ActivatedRoute, useValue: route }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ModelingEditorComponent);
                component = fixture.componentInstance;
                jest.spyOn(TestBed.inject(GuidedTourService), 'checkModelingComponent').mockReturnValue(of());
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('ngAfterViewInit', async () => {
        component.umlModel = classDiagram;
        fixture.detectChanges();

        // test
        component.ngAfterViewInit();
        const editor: ApollonEditor = component['apollonEditor'] as ApollonEditor;
        // Check that editor exists
        expect(editor).toBeDefined();
        await editor.nextRender;

        expect(Object.keys(editor.model.elements)).toEqual(Object.keys(classDiagram.elements));
    });

    it('ngOnDestroy', () => {
        component.umlModel = classDiagram;
        fixture.detectChanges();
        component.ngAfterViewInit();

        component.ngOnDestroy();
        // verify teardown
        expect(component['apollonEditor']).toBeUndefined();
    });

    it('ngOnChanges', async () => {
        // @ts-ignore
        const model = classDiagram;
        component.umlModel = model;
        fixture.detectChanges();
        component.ngAfterViewInit();

        const changedModel = cloneDeep(model) as any;
        changedModel.elements = {};
        changedModel.relationships = {};
        changedModel.interactive = { elements: {}, relationships: {} };
        changedModel.size = { height: 0, width: 0 };
        // note: using cloneDeep a default value exists, which would prevent the comparison below to pass, therefore we need to remove it here
        changedModel.default = undefined;
        // test
        await component.apollonEditor?.nextRender;
        component.ngOnChanges({
            umlModel: {
                currentValue: changedModel,
                previousValue: model,
            } as SimpleChange,
        });
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
        component.umlModel = classDiagram;
        fixture.detectChanges();
        component.ngAfterViewInit();

        // test
        // const model = component.getCurrentModel();
        // TODO: uncomment after deserialization bugfix in Apollon library, see https://github.com/ls1intum/Apollon/issues/146
        // expect(model).toEqual(testClassDiagram);
    });

    it('elementWithClass', () => {
        const model = classDiagram;
        component.umlModel = model;
        fixture.detectChanges();
        component.ngAfterViewInit();

        // test
        const umlElement = component.elementWithClass('Sibling 2', model);
        expect(umlElement?.id).toBe('e0dad7e7-f67b-4e4a-8845-6c5d801ea9ca');
    });

    it('elementWithAttribute', () => {
        const model = classDiagram;
        component.umlModel = model;
        fixture.detectChanges();
        component.ngAfterViewInit();

        // test
        const umlElement = component.elementWithAttribute('attribute', model);
        expect(umlElement?.id).toBe('6f572312-066b-4678-9c03-5032f3ba9be9');
    });

    it('elementWithMethod', () => {
        const model = classDiagram;
        component.umlModel = model;
        fixture.detectChanges();
        component.ngAfterViewInit();

        // test
        const umlElement = component.elementWithMethod('method', model);
        expect(umlElement?.id).toBe('11aae531-3244-4d07-8d60-b6210789ffa3');
    });

    it('should not show save indicator without savedStatus set', () => {
        component.savedStatus = undefined;
        component.readOnly = true;
        fixture.detectChanges();
        component.ngAfterViewInit();

        const statusHint = fixture.debugElement.query(By.css('.status-hint'));
        expect(statusHint).toBeNull();
    });

    it('should not show save indicator in read only mode', () => {
        component.savedStatus = { isSaving: false, isChanged: false };
        component.readOnly = true;
        fixture.detectChanges();
        component.ngAfterViewInit();

        const statusHint = fixture.debugElement.query(By.css('.status-hint'));
        expect(statusHint).toBeNull();
    });

    it('should not show save indicator in fullscreen mode', () => {
        component.savedStatus = { isSaving: false, isChanged: false };
        jest.spyOn(component, 'isFullScreen', 'get').mockReturnValue(true);
        fixture.detectChanges();
        component.ngAfterViewInit();

        const statusHint = fixture.debugElement.query(By.css('.status-hint'));
        expect(statusHint).toBeNull();
    });

    it('should show green checkmark save indicator if everything is saved', () => {
        component.savedStatus = { isSaving: false, isChanged: false };
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
        component.savedStatus = { isSaving: false, isChanged: true };
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
        component.savedStatus = { isSaving: true, isChanged: true };
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
        const spy = jest.spyOn(component.explanationChange, 'emit');

        const newExplanation = 'New Explanation';
        component.onExplanationInput(newExplanation);

        expect(spy).toHaveBeenCalledOnce();
        expect(spy).toHaveBeenCalledWith(newExplanation);
        expect(component.explanation).toBe(newExplanation);
    });

    it('should assess model for guided tour for all UML types', async () => {
        const guidedTourService = TestBed.inject(GuidedTourService);
        const subject = new Subject<string>();
        jest.spyOn(guidedTourService, 'checkModelingComponent').mockImplementation(() => subject.asObservable());
        fixture.detectChanges();
        const updateSpy = jest.spyOn(guidedTourService, 'updateModelingResult');
        updateSpy.mockReturnThis();

        let updateSpyCallCount = 0;
        let currentUmlName = personUML.name;

        await fixture.componentInstance.apollonEditor?.nextRender;
        subject.next(currentUmlName);
        expect(updateSpy).toHaveBeenLastCalledWith(currentUmlName, false);
        updateSpyCallCount++;
        expect(updateSpy).toHaveBeenCalledTimes(updateSpyCallCount);

        currentUmlName = studentUML.name;
        subject.next(currentUmlName);
        expect(updateSpy).toHaveBeenLastCalledWith(currentUmlName, false);

        updateSpyCallCount++;
        expect(updateSpy).toHaveBeenCalledTimes(updateSpyCallCount);

        currentUmlName = associationUML.name;
        subject.next(currentUmlName);
        expect(updateSpy).toHaveBeenLastCalledWith(currentUmlName, false);

        updateSpyCallCount++;
        expect(updateSpy).toHaveBeenCalledTimes(updateSpyCallCount);
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
