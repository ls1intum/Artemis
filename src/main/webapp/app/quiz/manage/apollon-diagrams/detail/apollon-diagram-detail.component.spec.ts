import { Course } from 'app/core/course/shared/entities/course.model';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ApollonDiagramService } from 'app/quiz/manage/apollon-diagrams/services/apollon-diagram.service';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { MockNgbModalService } from 'src/test/javascript/spec/helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { of } from 'rxjs';
import { AlertService } from 'app/shared/service/alert.service';
import { ApollonDiagram } from 'app/modeling/shared/entities/apollon-diagram.model';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';
import { ApollonDiagramDetailComponent } from 'app/quiz/manage/apollon-diagrams/detail/apollon-diagram-detail.component';
import { TranslateService } from '@ngx-translate/core';
import { MockProfileService } from 'src/test/javascript/spec/helpers/mocks/service/mock-profile.service';
import { MockLanguageHelper, MockTranslateService } from 'src/test/javascript/spec/helpers/mocks/service/mock-translate.service';
import { MockRouter } from 'src/test/javascript/spec/helpers/mocks/mock-router';
import * as testClassDiagram from 'src/test/javascript/spec/helpers/sample/modeling/test-models/class-diagram.json';
import { UMLDiagramType, UMLModel } from '@ls1intum/apollon';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { MockCourseManagementService } from 'src/test/javascript/spec/helpers/mocks/service/mock-course-management.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('ApollonDiagramDetail Component', () => {
    let apollonDiagramService: ApollonDiagramService;
    let fixture: ComponentFixture<ApollonDiagramDetailComponent>;

    const course: Course = { id: 123 } as Course;
    const diagram: ApollonDiagram = new ApollonDiagram(UMLDiagramType.ClassDiagram, course.id!);
    let alertService: AlertService;
    let modalService: NgbModal;
    let div: HTMLDivElement;
    // @ts-ignore
    const model = testClassDiagram as UMLModel;

    global.URL.createObjectURL = jest.fn(() => 'https://some.test.com');

    beforeEach(() => {
        const route = { params: of({ id: 1, courseId: 123 }), snapshot: { paramMap: convertToParamMap({ courseId: course.id }) } } as any as ActivatedRoute;
        diagram.id = 1;
        diagram.jsonRepresentation = JSON.stringify(testClassDiagram);
        TestBed.configureTestingModule({
            imports: [ApollonDiagramDetailComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                AlertService,
                JhiLanguageHelper,
                ApollonDiagramService,
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useClass: MockRouter },
                { provide: JhiLanguageHelper, useClass: MockLanguageHelper },
                { provide: CourseManagementService, useClass: MockCourseManagementService },
                { provide: ProfileService, useClass: MockProfileService },
            ],
        })
            .overrideTemplate(ApollonDiagramDetailComponent, '<div #editorContainer></div>')
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ApollonDiagramDetailComponent);
                apollonDiagramService = fixture.debugElement.injector.get(ApollonDiagramService);
                alertService = fixture.debugElement.injector.get(AlertService);
                modalService = fixture.debugElement.injector.get(NgbModal);
                div = fixture.componentInstance.editorContainer().nativeElement;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('initializeApollonEditor', () => {
        fixture.componentInstance.apollonDiagram = diagram;
        fixture.componentInstance.initializeApollonEditor(model);

        expect(fixture.componentInstance.apollonEditor).toBeTruthy();
    });

    it('save', async () => {
        jest.spyOn(console, 'error').mockImplementation(); // prevent: findDOMNode is deprecated and will be removed in the next major release
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
        fixture.componentInstance.apollonDiagram = diagram;
        const response: HttpResponse<ApollonDiagram> = new HttpResponse({ body: diagram });
        // TODO: we should mock this differently without require
        // eslint-disable-next-line @typescript-eslint/no-require-imports
        const svgRenderer = require('app/quiz/manage/apollon-diagrams/exercise-generation/svg-renderer');
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
        // TODO: we should mock this differently without require
        // eslint-disable-next-line @typescript-eslint/no-require-imports
        const module = require('app/quiz/manage/apollon-diagrams/exercise-generation/svg-renderer');
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
        const response: HttpResponse<ApollonDiagram> = new HttpResponse({ body: diagram });
        jest.spyOn(apollonDiagramService, 'find').mockReturnValue(of(response));

        // test
        fixture.componentInstance.ngOnInit();
        expect(fixture.componentInstance.apollonDiagram).toEqual(diagram);
        // clear the set time interval
        fixture.componentInstance.ngOnDestroy();
    });

    it('ngOnDestroy', async () => {
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
