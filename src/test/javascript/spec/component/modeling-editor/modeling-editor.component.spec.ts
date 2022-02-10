import { Course } from 'app/entities/course.model';
import { By } from '@angular/platform-browser';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { ApollonDiagram } from 'app/entities/apollon-diagram.model';
import { UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { UMLModel } from '@ls1intum/apollon';
import { Text } from '@ls1intum/apollon/lib/utils/svg/text';
import { ModelingEditorComponent } from 'app/exercises/modeling/shared/modeling-editor.component';
import * as testClassDiagram from '../../util/modeling/test-models/class-diagram.json';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { ArtemisTestModule } from '../../test.module';
import { cloneDeep } from 'lodash-es';
import { SimpleChange } from '@angular/core';
import { MockComponent, MockProvider } from 'ng-mocks';
import { ModelingExplanationEditorComponent } from 'app/exercises/modeling/shared/modeling-explanation-editor.component';
import { ApollonEditor } from '@ls1intum/apollon';

// has to be overridden, because jsdom does not provide a getBBox() function for SVGTextElements
Text.size = () => {
    return { width: 0, height: 0 };
};

describe('ModelingEditorComponent', () => {
    let fixture: ComponentFixture<ModelingEditorComponent>;
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
                jest.spyOn(TestBed.inject(GuidedTourService), 'checkModelingComponent').mockReturnValue(of());
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('ngAfterViewInit', () => {
        fixture.componentInstance.umlModel = classDiagram;
        fixture.detectChanges();

        // test
        fixture.componentInstance.ngAfterViewInit();
        const editor: ApollonEditor = fixture.componentInstance['apollonEditor'] as ApollonEditor;
        // Check that editor exists
        expect(editor).not.toBe(undefined);

        // check that editor contains elements of our model (direct equality check won't work somehow due to missing properties)
        expect(editor.model.elements.map((e) => e.id)).toEqual(classDiagram.elements.map((e) => e.id));
    });

    it('ngOnDestroy', () => {
        fixture.componentInstance.umlModel = classDiagram;
        fixture.detectChanges();
        fixture.componentInstance.ngAfterViewInit();

        fixture.componentInstance.ngOnDestroy();
        // verify teardown
        expect(fixture.componentInstance['apollonEditor']).toBe(undefined);
    });

    it('ngOnChanges', () => {
        // @ts-ignore
        const model = classDiagram;
        fixture.componentInstance.umlModel = model;
        fixture.detectChanges();
        fixture.componentInstance.ngAfterViewInit();

        const changedModel = cloneDeep(model) as any;
        changedModel.elements = [];
        changedModel.relationships = [];
        changedModel.interactive = { elements: [], relationships: [] };
        changedModel.size = { height: 0, width: 0 };
        // note: using cloneDeep a default value exists, which would prevent the comparison below to pass, therefore we need to remove it here
        changedModel.default = undefined;

        // test
        fixture.componentInstance.ngOnChanges({
            umlModel: {
                currentValue: changedModel,
                previousValue: model,
            } as SimpleChange,
        });
        const componentModel = fixture.componentInstance['apollonEditor']!.model as UMLModel;
        expect(componentModel).toEqual(changedModel);
    });

    it('isFullScreen false', () => {
        // test
        const fullScreen = fixture.componentInstance.isFullScreen;
        expect(fullScreen).toBe(false);
    });

    it('getCurrentModel', () => {
        fixture.componentInstance.umlModel = classDiagram;
        fixture.detectChanges();
        fixture.componentInstance.ngAfterViewInit();

        // test
        // const model = fixture.componentInstance.getCurrentModel();
        // TODO: uncomment after deserialization bugfix in Apollon library, see https://github.com/ls1intum/Apollon/issues/146
        // expect(model).toEqual(testClassDiagram);
    });

    it('elementWithClass', () => {
        const model = classDiagram;
        fixture.componentInstance.umlModel = model;
        fixture.detectChanges();
        fixture.componentInstance.ngAfterViewInit();

        // test
        const umlElement = fixture.componentInstance.elementWithClass('Sibling 2', model);
        expect(umlElement?.id).toBe('e0dad7e7-f67b-4e4a-8845-6c5d801ea9ca');
    });

    it('elementWithAttribute', () => {
        const model = classDiagram;
        fixture.componentInstance.umlModel = model;
        fixture.detectChanges();
        fixture.componentInstance.ngAfterViewInit();

        // test
        const umlElement = fixture.componentInstance.elementWithAttribute('attribute', model);
        expect(umlElement?.id).toBe('6f572312-066b-4678-9c03-5032f3ba9be9');
    });

    it('elementWithMethod', () => {
        const model = classDiagram;
        fixture.componentInstance.umlModel = model;
        fixture.detectChanges();
        fixture.componentInstance.ngAfterViewInit();

        // test
        const umlElement = fixture.componentInstance.elementWithMethod('method', model);
        expect(umlElement?.id).toBe('11aae531-3244-4d07-8d60-b6210789ffa3');
    });

    it('should not show save indicator without savedStatus set', () => {
        fixture.componentInstance.savedStatus = undefined;
        fixture.componentInstance.readOnly = true;
        fixture.detectChanges();
        fixture.componentInstance.ngAfterViewInit();

        const statusHint = fixture.debugElement.query(By.css('.status-hint'));
        expect(statusHint).toBe(null);
    });

    it('should not show save indicator in read only mode', () => {
        fixture.componentInstance.savedStatus = { isSaving: false, isChanged: false };
        fixture.componentInstance.readOnly = true;
        fixture.detectChanges();
        fixture.componentInstance.ngAfterViewInit();

        const statusHint = fixture.debugElement.query(By.css('.status-hint'));
        expect(statusHint).toBe(null);
    });

    it('should not show save indicator in fullscreen mode', () => {
        fixture.componentInstance.savedStatus = { isSaving: false, isChanged: false };
        jest.spyOn(fixture.componentInstance, 'isFullScreen', 'get').mockReturnValue(true);
        fixture.detectChanges();
        fixture.componentInstance.ngAfterViewInit();

        const statusHint = fixture.debugElement.query(By.css('.status-hint'));
        expect(statusHint).toBe(null);
    });

    it('should show green checkmark save indicator if everything is saved', () => {
        fixture.componentInstance.savedStatus = { isSaving: false, isChanged: false };
        fixture.detectChanges();
        fixture.componentInstance.ngAfterViewInit();

        const statusHint = fixture.debugElement.query(By.css('.status-hint.text-success'));
        expect(statusHint).not.toBe(null);

        const icon = statusHint.query(By.css('fa-icon'));
        expect(icon).not.toBe(null);

        const spanText = statusHint.query(By.css('span'))?.nativeElement?.textContent;
        expect(spanText).toBe('All changes saved');
    });

    it('should show yellow times save indicator if something is unsaved', () => {
        fixture.componentInstance.savedStatus = { isSaving: false, isChanged: true };
        fixture.detectChanges();
        fixture.componentInstance.ngAfterViewInit();

        const statusHint = fixture.debugElement.query(By.css('.status-hint.text-warning'));
        expect(statusHint).not.toBe(null);

        const icon = statusHint.query(By.css('fa-icon'));
        expect(icon).not.toBe(null);

        const spanText = statusHint.query(By.css('span'))?.nativeElement?.textContent;
        expect(spanText).toBe('Unsaved changes');
    });

    it('should show saving indicator if it is currently saving', () => {
        fixture.componentInstance.savedStatus = { isSaving: true, isChanged: true };
        fixture.detectChanges();
        fixture.componentInstance.ngAfterViewInit();

        const statusHint = fixture.debugElement.query(By.css('.status-hint.text-info'));
        expect(statusHint).not.toBe(null);

        const icon = statusHint.query(By.css('fa-icon'));
        expect(icon).not.toBe(null);

        const spanText = statusHint.query(By.css('span'))?.nativeElement?.textContent;
        expect(spanText).toBe('Saving...');
    });
});
