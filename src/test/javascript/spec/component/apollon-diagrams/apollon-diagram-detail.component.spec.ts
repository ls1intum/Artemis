import { Course } from 'app/entities/course.model';
import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { ApollonDiagramService } from 'app/exercises/quiz/manage/apollon-diagrams/apollon-diagram.service';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { of } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';
import { ApollonDiagram } from 'app/entities/apollon-diagram.model';
import { UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { HttpResponse } from '@angular/common/http';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { ApollonDiagramDetailComponent } from 'app/exercises/quiz/manage/apollon-diagrams/apollon-diagram-detail.component';
import { TranslateService } from '@ngx-translate/core';
import { MockLanguageHelper, MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { MockRouter } from '../../helpers/mocks/mock-router';
import * as testClassDiagram from '../../util/modeling/test-models/class-diagram.json';
import { UMLModel } from '@ls1intum/apollon';
import { ElementRef } from '@angular/core';
import { Text } from '@ls1intum/apollon/lib/es5/utils/svg/text';

// has to be overridden, because jsdom does not provide a getBBox() function for SVGTextElements
Text.size = () => {
    return { width: 0, height: 0 };
};

describe('ApollonDiagramDetail Component', () => {
    let apollonDiagramService: ApollonDiagramService;
    let fixture: ComponentFixture<ApollonDiagramDetailComponent>;

    const course: Course = { id: 123 } as Course;
    const diagram: ApollonDiagram = new ApollonDiagram(UMLDiagramType.ClassDiagram, course.id!);
    let modalService: NgbModal;
    let alertService: AlertService;
    // @ts-ignore
    const model = testClassDiagram as UMLModel;

    global.URL.createObjectURL = jest.fn(() => 'https://some.test.com');
    global.URL.revokeObjectURL = jest.fn(() => '');

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
            ],
            schemas: [],
        })
            .overrideTemplate(ApollonDiagramDetailComponent, '')
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ApollonDiagramDetailComponent);
                apollonDiagramService = fixture.debugElement.injector.get(ApollonDiagramService);
                modalService = fixture.debugElement.injector.get(NgbModal);
                alertService = fixture.debugElement.injector.get(AlertService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('ngOnInit', fakeAsync(() => {
        const div = document.createElement('div');
        fixture.componentInstance.editorContainer = new ElementRef(div);
        const response: HttpResponse<ApollonDiagram> = new HttpResponse({ body: diagram });
        jest.spyOn(apollonDiagramService, 'find').mockReturnValue(of(response));

        // test
        fixture.componentInstance.ngOnInit();
        tick(500);
        expect(fixture.componentInstance.apollonDiagram).toEqual(diagram);
        flush();
        // clear the set time interval
        fixture.componentInstance.ngOnDestroy();
    }));

    it('ngOnDestroy', fakeAsync(() => {
        const div = document.createElement('div');
        fixture.componentInstance.editorContainer = new ElementRef(div);
        const response: HttpResponse<ApollonDiagram> = new HttpResponse({ body: diagram });
        jest.spyOn(apollonDiagramService, 'find').mockReturnValue(of(response));
        fixture.componentInstance.ngOnInit();
        // ApollonEditor is the child
        tick(500);
        expect(div.children).toHaveLength(1);

        // create spy after ngOnInit
        jest.spyOn(global, 'clearInterval');

        // test
        fixture.componentInstance.ngOnDestroy();
        tick(500);
        expect(div.children).toHaveLength(0);
        expect(clearInterval).toHaveBeenCalledOnce();
        expect(clearInterval).toHaveBeenCalledWith(fixture.componentInstance.autoSaveInterval);
    }));

    it('initializeApollonEditor', fakeAsync(() => {
        const div = document.createElement('div');
        fixture.componentInstance.editorContainer = new ElementRef(div);
        fixture.componentInstance.apollonDiagram = diagram;

        fixture.componentInstance.initializeApollonEditor(model);
        tick(500);
        expect(fixture.componentInstance.apollonEditor).toBeTruthy();
    }));

    it('downloadSelection', fakeAsync(() => {
        const div = document.createElement('div');
        fixture.componentInstance.editorContainer = new ElementRef(div);
        const module = require('app/exercises/quiz/manage/apollon-diagrams/exercise-generation/svg-renderer');
        jest.spyOn(module, 'convertRenderedSVGToPNG').mockReturnValue(new Blob([]));
        fixture.componentInstance.apollonDiagram = diagram;
        fixture.componentInstance.initializeApollonEditor(model);
        // ApollonEditor is the child
        expect(div.children).toHaveLength(1);

        // set selection
        fixture.componentInstance.apollonEditor!.selection = { elements: model.elements.map((element) => element.id), relationships: [] };
        fixture.detectChanges();

        // test
        fixture.componentInstance.downloadSelection().then(() => {
            tick(500);
            // last task when downloading file
            expect(window.URL.revokeObjectURL).toHaveBeenCalledOnce();
        });
    }));

    it('save', fakeAsync(() => {
        // setup
        const response: HttpResponse<ApollonDiagram> = new HttpResponse({ body: diagram });
        const updateStub = jest.spyOn(apollonDiagramService, 'update').mockReturnValue(of(response));

        const div = document.createElement('div');
        fixture.componentInstance.editorContainer = new ElementRef(div);
        fixture.componentInstance.apollonDiagram = diagram;

        fixture.componentInstance.initializeApollonEditor(model);
        expect(fixture.componentInstance.apollonEditor).toBeTruthy();

        // test
        fixture.componentInstance.saveDiagram();
        tick(500);
        expect(updateStub).toHaveBeenCalledOnce();
        flush();
        // clear the set time interval
        fixture.componentInstance.ngOnDestroy();
    }));

    it('generateExercise', fakeAsync(() => {
        // setup
        const response: HttpResponse<ApollonDiagram> = new HttpResponse({ body: diagram });
        jest.spyOn(apollonDiagramService, 'update').mockReturnValue(of(response));

        const div = document.createElement('div');
        fixture.componentInstance.editorContainer = new ElementRef(div);
        fixture.componentInstance.apollonDiagram = diagram;

        fixture.componentInstance.initializeApollonEditor(model);
        fixture.detectChanges();
        expect(fixture.componentInstance.apollonEditor).toBeTruthy();

        const result = new Promise((resolve) => resolve(true));
        jest.spyOn(modalService, 'open').mockReturnValue(<NgbModalRef>{ componentInstance: fixture.componentInstance, result });
        const successSpy = jest.spyOn(alertService, 'success');

        // test
        fixture.componentInstance.generateExercise().then(() => {
            expect(successSpy).toHaveBeenCalledOnce();
        });
        tick(500);
        flush();
        // clear the set time interval
        fixture.componentInstance.ngOnDestroy();
    }));
});
