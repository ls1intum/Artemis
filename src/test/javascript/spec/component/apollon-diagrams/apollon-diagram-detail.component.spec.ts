import { Course } from 'app/entities/course.model';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ApollonDiagramService } from 'app/exercises/quiz/manage/apollon-diagrams/apollon-diagram.service';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { of } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';
import { ApollonDiagram } from 'app/entities/apollon-diagram.model';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { HttpResponse } from '@angular/common/http';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { ApollonDiagramDetailComponent } from 'app/exercises/quiz/manage/apollon-diagrams/apollon-diagram-detail.component';
import { TranslateService } from '@ngx-translate/core';
import { MockLanguageHelper, MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { MockRouter } from '../../helpers/mocks/mock-router';
import * as testClassDiagram from '../../util/modeling/test-models/class-diagram.json';
import { UMLDiagramType, UMLModel } from '@ls1intum/apollon';
import { ElementRef } from '@angular/core';
import { Text } from '@ls1intum/apollon/lib/es5/utils/svg/text';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { MockCourseManagementService } from '../../helpers/mocks/service/mock-course-management.service';

// has to be overridden, because jsdom does not provide a getBBox() function for SVGTextElements
Text.size = () => {
    return { width: 0, height: 0 };
};

describe('ApollonDiagramDetail Component', () => {
    let apollonDiagramService: ApollonDiagramService;
    let fixture: ComponentFixture<ApollonDiagramDetailComponent>;

    const course: Course = { id: 123 } as Course;
    const diagram: ApollonDiagram = new ApollonDiagram(UMLDiagramType.ClassDiagram, course.id!);
    let alertService: AlertService;
    let modalService: NgbModal;
    // @ts-ignore
    const model = testClassDiagram as UMLModel;

    global.URL.createObjectURL = jest.fn(() => 'https://some.test.com');

    beforeEach(() => {
        const route = { params: of({ id: 1, courseId: 123 }), snapshot: { paramMap: convertToParamMap({ courseId: course.id }) } } as any as ActivatedRoute;
        diagram.id = 1;
        diagram.jsonRepresentation = JSON.stringify(testClassDiagram);
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            declarations: [ApollonDiagramDetailComponent],
            providers: [
                AlertService,
                JhiLanguageHelper,
                ApollonDiagramService,
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useClass: MockRouter },
                { provide: JhiLanguageHelper, useClass: MockLanguageHelper },
                { provide: CourseManagementService, useClass: MockCourseManagementService },
            ],
            schemas: [],
        })
            .overrideTemplate(ApollonDiagramDetailComponent, '')
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ApollonDiagramDetailComponent);
                apollonDiagramService = fixture.debugElement.injector.get(ApollonDiagramService);
                alertService = fixture.debugElement.injector.get(AlertService);
                modalService = fixture.debugElement.injector.get(NgbModal);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('initializeApollonEditor', () => {
        const div = document.createElement('div');
        fixture.componentInstance.editorContainer = new ElementRef(div);
        fixture.componentInstance.apollonDiagram = diagram;
        fixture.componentInstance.initializeApollonEditor(model);

        expect(fixture.componentInstance.apollonEditor).toBeTruthy();
    });

    it('save', async () => {
        const div = document.createElement('div');
        fixture.componentInstance.editorContainer = new ElementRef(div);
        fixture.componentInstance.apollonDiagram = diagram;
        // setup
        const response: HttpResponse<ApollonDiagram> = new HttpResponse({ body: diagram });
        const updateStub = jest.spyOn(apollonDiagramService, 'update').mockReturnValue(of(response));

        fixture.componentInstance.initializeApollonEditor(model);
        expect(fixture.componentInstance.apollonEditor).toBeTruthy();

        // test
        await fixture.componentInstance.apollonEditor?.nextRender;
        await fixture.componentInstance.saveDiagram();
        expect(updateStub).toHaveBeenCalledOnce();
        // clear the set time interval
        fixture.componentInstance.ngOnDestroy();
    });

    it('generateExercise', async () => {
        // setup
        const div = document.createElement('div');
        fixture.componentInstance.editorContainer = new ElementRef(div);
        fixture.componentInstance.apollonDiagram = diagram;
        const response: HttpResponse<ApollonDiagram> = new HttpResponse({ body: diagram });
        const svgRenderer = require('app/exercises/quiz/manage/apollon-diagrams/exercise-generation/svg-renderer');
        jest.spyOn(svgRenderer, 'convertRenderedSVGToPNG').mockReturnValue(of(new Blob()));
        jest.spyOn(apollonDiagramService, 'update').mockReturnValue(of(response));

        fixture.componentInstance.initializeApollonEditor(model);
        expect(fixture.componentInstance.apollonEditor).toBeTruthy();
        fixture.detectChanges();

        const emitSpy = jest.spyOn(fixture.componentInstance.closeEdit, 'emit');

        // test
        await fixture.componentInstance.apollonEditor?.nextRender;
        await fixture.componentInstance.generateExercise();

        expect(emitSpy).toHaveBeenCalledOnce();

        // clear the set time interval
        fixture.componentInstance.ngOnDestroy();
    });

    it('validateGeneration', async () => {
        const nonInteractiveModel = { ...model, interactive: { ...model.interactive, elements: {}, relationships: {} } };

        // setup
        const div = document.createElement('div');
        fixture.componentInstance.editorContainer = new ElementRef(div);
        fixture.componentInstance.apollonDiagram = diagram;
        fixture.componentInstance.initializeApollonEditor(nonInteractiveModel);
        const errorSpy = jest.spyOn(alertService, 'error');

        // test
        await fixture.componentInstance.apollonEditor?.nextRender;
        await fixture.componentInstance.generateExercise();
        expect(errorSpy).toHaveBeenCalledOnce();

        // clear the set time interval
        fixture.componentInstance.ngOnDestroy();
    });

    it('downloadSelection', async () => {
        const div = document.createElement('div');
        fixture.componentInstance.editorContainer = new ElementRef(div);
        const module = require('app/exercises/quiz/manage/apollon-diagrams/exercise-generation/svg-renderer');
        jest.spyOn(module, 'convertRenderedSVGToPNG').mockReturnValue(new Blob([]));
        fixture.componentInstance.apollonDiagram = diagram;
        fixture.componentInstance.initializeApollonEditor(model);
        // ApollonEditor is the child

        await fixture.componentInstance.apollonEditor?.nextRender;
        expect(div.children).toHaveLength(1);

        // set selection
        fixture.componentInstance.apollonEditor!.selection = {
            elements: Object.fromEntries(Object.keys(model.elements).map((key) => [key, true])),
            relationships: {},
        };
        fixture.detectChanges();
        // test
        await fixture.componentInstance.downloadSelection();
        expect(window.URL.createObjectURL).toHaveBeenCalledOnce();
    });

    it('confirmExitDetailView', () => {
        const openModalSpy = jest.spyOn(modalService, 'open');
        const emitCloseModalSpy = jest.spyOn(fixture.componentInstance.closeModal, 'emit');
        const emitCloseEditSpy = jest.spyOn(fixture.componentInstance.closeEdit, 'emit');

        fixture.componentInstance.isSaved = true;
        fixture.componentInstance.confirmExitDetailView(true);
        expect(emitCloseModalSpy).toHaveBeenCalledOnce();
        fixture.componentInstance.confirmExitDetailView(false);
        expect(emitCloseEditSpy).toHaveBeenCalledOnce();

        fixture.componentInstance.isSaved = false;
        fixture.componentInstance.confirmExitDetailView(true);
        expect(openModalSpy).toHaveBeenCalledOnce();
    });

    it('ngOnInit', async () => {
        const div = document.createElement('div');
        fixture.componentInstance.editorContainer = new ElementRef(div);
        const response: HttpResponse<ApollonDiagram> = new HttpResponse({ body: diagram });
        jest.spyOn(apollonDiagramService, 'find').mockReturnValue(of(response));

        // test
        fixture.componentInstance.ngOnInit();
        expect(fixture.componentInstance.apollonDiagram).toEqual(diagram);
        // clear the set time interval
        fixture.componentInstance.ngOnDestroy();
    });

    it('ngOnDestroy', async () => {
        const div = document.createElement('div');
        fixture.componentInstance.editorContainer = new ElementRef(div);
        const response: HttpResponse<ApollonDiagram> = new HttpResponse({ body: diagram });
        jest.spyOn(apollonDiagramService, 'find').mockReturnValue(of(response));
        fixture.componentInstance.ngOnInit();
        expect(div.children).toHaveLength(0);

        // create spy after ngOnInit
        jest.spyOn(global, 'clearInterval');

        // test
        fixture.componentInstance.ngOnDestroy();
        expect(div.children).toHaveLength(0);
        expect(clearInterval).toHaveBeenCalledOnce();
        expect(clearInterval).toHaveBeenCalledWith(fixture.componentInstance.autoSaveInterval);
    });
});
