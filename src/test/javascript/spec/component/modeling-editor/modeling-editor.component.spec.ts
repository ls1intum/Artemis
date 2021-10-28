import { Course } from 'app/entities/course.model';
import * as sinon from 'sinon';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
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
import { Renderer2, SimpleChange } from '@angular/core';
import { MockComponent, MockProvider } from 'ng-mocks';
import { ModelingExplanationEditorComponent } from 'app/exercises/modeling/shared/modeling-explanation-editor.component';
import { stub } from 'sinon';

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
            providers: [
                MockProvider(GuidedTourService),
                MockProvider(Renderer2),
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: ActivatedRoute, useValue: route },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ModelingEditorComponent);
                stub(TestBed.inject(GuidedTourService), 'checkModelingComponent').returns(of());
            });
    });

    afterEach(() => {
        sinon.restore();
    });

    it('ngAfterViewInit', () => {
        fixture.componentInstance.umlModel = classDiagram;
        fixture.detectChanges();

        // test
        fixture.componentInstance.ngAfterViewInit();
        expect(fixture.componentInstance['apollonEditor']).toBeTruthy();
    });

    it('ngOnDestroy', () => {
        fixture.componentInstance.umlModel = classDiagram;
        fixture.detectChanges();
        fixture.componentInstance.ngAfterViewInit();
        expect(fixture.componentInstance['apollonEditor']).toBeTruthy();

        // test
        fixture.componentInstance.ngOnDestroy();
        expect(fixture.componentInstance['apollonEditor']).toBeFalsy();
    });

    it('ngOnChanges', () => {
        // @ts-ignore
        const model = classDiagram;
        fixture.componentInstance.umlModel = model;
        fixture.detectChanges();
        fixture.componentInstance.ngAfterViewInit();
        expect(fixture.componentInstance['apollonEditor']).toBeTruthy();

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
        expect(fullScreen).toBeFalsy();
    });

    it('getCurrentModel', () => {
        fixture.componentInstance.umlModel = classDiagram;
        fixture.detectChanges();
        fixture.componentInstance.ngAfterViewInit();
        expect(fixture.componentInstance['apollonEditor']).toBeTruthy();

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
        expect(fixture.componentInstance['apollonEditor']).toBeTruthy();

        // test
        const umlElement = fixture.componentInstance.elementWithClass('Sibling 2', model);
        expect(umlElement).toBeTruthy();
    });

    it('elementWithAttribute', () => {
        const model = classDiagram;
        fixture.componentInstance.umlModel = model;
        fixture.detectChanges();
        fixture.componentInstance.ngAfterViewInit();
        expect(fixture.componentInstance['apollonEditor']).toBeTruthy();

        // test
        const umlElement = fixture.componentInstance.elementWithAttribute('attribute', model);
        expect(umlElement).toBeTruthy();
    });

    it('elementWithMethod', () => {
        const model = classDiagram;
        fixture.componentInstance.umlModel = model;
        fixture.detectChanges();
        fixture.componentInstance.ngAfterViewInit();
        expect(fixture.componentInstance['apollonEditor']).toBeTruthy();

        // test
        const umlElement = fixture.componentInstance.elementWithMethod('method', model);
        expect(umlElement).toBeTruthy();
    });
});
