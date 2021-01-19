import { Course } from 'app/entities/course.model';
import * as sinon from 'sinon';
import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { ApollonDiagramService } from 'app/exercises/quiz/manage/apollon-diagrams/apollon-diagram.service';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { of } from 'rxjs';
import { JhiAlertService } from 'ng-jhipster';
import { ApollonDiagram } from 'app/entities/apollon-diagram.model';
import { UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { HttpResponse } from '@angular/common/http';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { ApollonDiagramDetailComponent } from 'app/exercises/quiz/manage/apollon-diagrams/apollon-diagram-detail.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { MockRouter } from '../../helpers/mocks/service/mock-route.service';
import * as testClassDiagram from '../../util/modeling/test-models/class-diagram.json';
import { UMLModel, Selection } from '@ls1intum/apollon';
import { ElementRef } from '@angular/core';
import { Text } from '@ls1intum/apollon/lib/utils/svg/text';

// has to be overridden, because jsdom does not provide a getBBox() function for SVGTextElements
Text.size = () => {
    return { width: 0, height: 0 };
};

describe('ApollonDiagramDetail Component', () => {
    let apollonDiagramService: ApollonDiagramService;
    let fixture: ComponentFixture<ApollonDiagramDetailComponent>;
    const sandbox = sinon.createSandbox();
    const course: Course = { id: 123 } as Course;
    const diagram: ApollonDiagram = new ApollonDiagram(UMLDiagramType.ClassDiagram, course.id!);
    let modalService: NgbModal;
    let jhiAlertService: JhiAlertService;

    global.URL.createObjectURL = jest.fn(() => 'http://some.test.com');
    global.URL.revokeObjectURL = jest.fn(() => '');

    beforeEach(() => {
        const route = ({ params: of({ id: 1, courseId: 123 }), snapshot: { paramMap: convertToParamMap({ courseId: course.id }) } } as any) as ActivatedRoute;
        diagram.id = 1;
        diagram.jsonRepresentation = JSON.stringify(testClassDiagram);

        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            declarations: [ApollonDiagramDetailComponent],
            providers: [
                JhiAlertService,
                JhiLanguageHelper,
                ApollonDiagramService,
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useValue: MockRouter },
            ],
            schemas: [],
        })
            .overrideTemplate(ApollonDiagramDetailComponent, '')
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ApollonDiagramDetailComponent);
                apollonDiagramService = fixture.debugElement.injector.get(ApollonDiagramService);
                modalService = fixture.debugElement.injector.get(NgbModal);
                jhiAlertService = fixture.debugElement.injector.get(JhiAlertService);
            });
    });

    afterEach(function () {
        sandbox.restore();
    });

    it('ngOnInit', () => {
        const response: HttpResponse<ApollonDiagram> = new HttpResponse({ body: diagram });
        sandbox.stub(apollonDiagramService, 'find').returns(of(response));

        // test
        fixture.componentInstance.ngOnInit();
        expect(fixture.componentInstance.apollonDiagram).toEqual(diagram);
    });

    it('ngOnDestroy', () => {
        const div = document.createElement('div');
        fixture.componentInstance.editorContainer = new ElementRef(div);
        const response: HttpResponse<ApollonDiagram> = new HttpResponse({ body: diagram });
        sandbox.stub(apollonDiagramService, 'find').returns(of(response));
        fixture.componentInstance.ngOnInit();
        // ApollonEditor is the child
        expect(div.children.length).toEqual(1);

        // create spy after ngOnInit
        sandbox.spy(global, 'clearInterval');

        // test
        fixture.componentInstance.ngOnDestroy();
        expect(div.children.length).toEqual(0);
        expect(clearInterval).toBeCalledTimes(1);
        expect(clearInterval).toBeCalledWith(fixture.componentInstance.autoSaveInterval);
    });

    it('initializeApollonEditor', () => {
        const div = document.createElement('div');
        fixture.componentInstance.editorContainer = new ElementRef(div);
        fixture.componentInstance.apollonDiagram = diagram;
        // @ts-ignore
        const model: UMLModel = testClassDiagram as UMLModel;
        fixture.componentInstance.initializeApollonEditor(model);
        expect(fixture.componentInstance.apollonEditor).toBeTruthy();
    });

    it('downloadSelection', fakeAsync(() => {
        // setup
        // @ts-ignore
        const model: UMLModel = testClassDiagram as UMLModel;
        const div = document.createElement('div');
        fixture.componentInstance.editorContainer = new ElementRef(div);
        const module = require('app/exercises/quiz/manage/apollon-diagrams/exercise-generation/svg-renderer');
        sandbox.stub(module, 'convertRenderedSVGToPNG').returns(new Blob([]));
        fixture.componentInstance.apollonDiagram = diagram;
        fixture.componentInstance.initializeApollonEditor(model);
        // ApollonEditor is the child
        expect(div.children.length).toEqual(1);

        // set selection
        const selection: Selection = { elements: model.elements.map((element) => element.id), relationships: [] };

        fixture.componentInstance.apollonEditor!.selection = selection;

        // test
        fixture.componentInstance.downloadSelection();
        tick();
        // last task when downloading file
        expect(window.URL.revokeObjectURL).toBeCalled();
        flush();
    }));

    it('save', () => {
        // setup
        const response: HttpResponse<ApollonDiagram> = new HttpResponse({ body: diagram });
        const updateStub = sandbox.stub(apollonDiagramService, 'update').returns(of(response));

        const div = document.createElement('div');
        fixture.componentInstance.editorContainer = new ElementRef(div);
        fixture.componentInstance.apollonDiagram = diagram;

        // @ts-ignore
        const model: UMLModel = testClassDiagram as UMLModel;
        fixture.componentInstance.initializeApollonEditor(model);
        expect(fixture.componentInstance.apollonEditor).toBeTruthy();

        // test
        fixture.componentInstance.saveDiagram();
        expect(updateStub).toBeCalled();
    });

    it('generateExercise', fakeAsync(() => {
        // setup
        const response: HttpResponse<ApollonDiagram> = new HttpResponse({ body: diagram });
        sandbox.stub(apollonDiagramService, 'update').returns(of(response));

        const div = document.createElement('div');
        fixture.componentInstance.editorContainer = new ElementRef(div);
        fixture.componentInstance.apollonDiagram = diagram;

        // @ts-ignore
        const model: UMLModel = testClassDiagram as UMLModel;
        fixture.componentInstance.initializeApollonEditor(model);
        expect(fixture.componentInstance.apollonEditor).toBeTruthy();

        const result = new Promise((resolve) => resolve(true));
        sandbox.stub(modalService, 'open').returns(<NgbModalRef>{ componentInstance: fixture.componentInstance, result });
        const successSpy = sandbox.spy(jhiAlertService, 'success');

        // test
        fixture.componentInstance.generateExercise();
        tick();
        expect(successSpy).toBeCalled();
        flush();
    }));
});
