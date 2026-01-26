import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
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
import { ApollonEditor, UMLDiagramType, UMLModel } from '@tumaet/apollon';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { MockCourseManagementService } from 'src/test/javascript/spec/helpers/mocks/service/mock-course-management.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import * as SVGRendererAPI from 'app/quiz/manage/apollon-diagrams/exercise-generation/svg-renderer';

describe('ApollonDiagramDetail Component', () => {
    setupTestBed({ zoneless: true });

    let apollonDiagramService: ApollonDiagramService;
    let courseService: CourseManagementService;
    let fixture: ComponentFixture<ApollonDiagramDetailComponent>;

    const course: Course = { id: 123 } as Course;
    const diagram: ApollonDiagram = new ApollonDiagram(UMLDiagramType.ClassDiagram, course.id!);
    let alertService: AlertService;
    let modalService: NgbModal;
    let div: HTMLDivElement;
    // @ts-ignore
    const model = testClassDiagram as UMLModel;

    globalThis.URL.createObjectURL = vi.fn(() => 'https://some.test.com');

    beforeEach(async () => {
        const route = { params: of({ id: 1, courseId: 123 }), snapshot: { paramMap: convertToParamMap({ courseId: course.id }) } } as any as ActivatedRoute;
        diagram.id = 1;
        diagram.jsonRepresentation = JSON.stringify(testClassDiagram);
        await TestBed.configureTestingModule({
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
            .compileComponents();

        fixture = TestBed.createComponent(ApollonDiagramDetailComponent);
        apollonDiagramService = fixture.debugElement.injector.get(ApollonDiagramService);
        courseService = fixture.debugElement.injector.get(CourseManagementService);
        alertService = fixture.debugElement.injector.get(AlertService);
        modalService = fixture.debugElement.injector.get(NgbModal);
        div = fixture.componentInstance.editorContainer().nativeElement;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('initializeApollonEditor', async () => {
        vi.spyOn(console, 'error').mockImplementation(() => {}); // prevent: findDOMNode is deprecated and will be removed in the next major release
        fixture.componentInstance.apollonDiagram.set(diagram);
        await fixture.componentInstance.initializeApollonEditor(model);

        expect(fixture.componentInstance.apollonEditor).toBeTruthy();
    });

    it('save', async () => {
        vi.spyOn(console, 'error').mockImplementation(() => {}); // prevent: findDOMNode is deprecated and will be removed in the next major release
        fixture.componentInstance.apollonDiagram.set(diagram);
        // setup
        const response: HttpResponse<ApollonDiagram> = new HttpResponse({ body: diagram });
        const updateStub = vi.spyOn(apollonDiagramService, 'update').mockReturnValue(of(response));

        await fixture.componentInstance.initializeApollonEditor(model);
        expect(fixture.componentInstance.apollonEditor).toBeTruthy();

        // test
        await fixture.componentInstance.saveDiagram();
        expect(updateStub).toHaveBeenCalledOnce();
        // clear the set time interval
        fixture.componentInstance.ngOnDestroy();
    });

    it('generateExercise', async () => {
        // setup
        fixture.componentInstance.apollonDiagram.set(diagram);
        const response: HttpResponse<ApollonDiagram> = new HttpResponse({ body: diagram });
        vi.spyOn(SVGRendererAPI, 'convertRenderedSVGToPNG').mockResolvedValue(new Blob());
        vi.spyOn(apollonDiagramService, 'update').mockReturnValue(of(response));

        // Mock ApollonEditor.exportModelAsSvg to avoid DOM issues in jsdom
        vi.spyOn(ApollonEditor, 'exportModelAsSvg').mockResolvedValue({
            svg: '<svg></svg>',
            clip: { x: 0, y: 0, width: 100, height: 100 },
        });

        await fixture.componentInstance.initializeApollonEditor(model);
        expect(fixture.componentInstance.apollonEditor).toBeTruthy();
        fixture.detectChanges();

        const emitSpy = vi.spyOn(fixture.componentInstance.closeEdit, 'emit');

        // test
        await fixture.componentInstance.generateExercise();

        expect(emitSpy).toHaveBeenCalledOnce();

        // clear the set time interval
        fixture.componentInstance.ngOnDestroy();
    });

    it('validateGeneration', async () => {
        const nonInteractiveModel = { ...model, nodes: [], edges: [] } as any;

        // setup
        fixture.componentInstance.apollonDiagram.set(diagram);
        await fixture.componentInstance.initializeApollonEditor(nonInteractiveModel);
        const errorSpy = vi.spyOn(alertService, 'error');

        // test
        await fixture.componentInstance.generateExercise();
        expect(errorSpy).toHaveBeenCalledOnce();

        // clear the set time interval
        fixture.componentInstance.ngOnDestroy();
    });

    it('downloadSelection', async () => {
        vi.spyOn(SVGRendererAPI, 'convertRenderedSVGToPNG').mockResolvedValue(new Blob([]));
        fixture.componentInstance.apollonDiagram.set(diagram);
        await fixture.componentInstance.initializeApollonEditor(model);
        // ApollonEditor is the child

        expect(div.children).toHaveLength(1);

        // set selection (use `as any` since selection property may not exist in newer ApollonEditor API)
        (fixture.componentInstance.apollonEditor as any).selection = {
            elements: Object.fromEntries(((model as any).nodes ?? []).map((node: any) => [node.id, true])),
            relationships: {},
        };
        fixture.detectChanges();
        // test
        await fixture.componentInstance.downloadSelection();
        expect(window.URL.createObjectURL).toHaveBeenCalledOnce();
    });

    it('confirmExitDetailView', () => {
        const openModalSpy = vi.spyOn(modalService, 'open');
        const emitCloseModalSpy = vi.spyOn(fixture.componentInstance.closeModal, 'emit');
        const emitCloseEditSpy = vi.spyOn(fixture.componentInstance.closeEdit, 'emit');

        fixture.componentInstance.isSaved = true;
        fixture.componentInstance.confirmExitDetailView(true);
        expect(emitCloseModalSpy).toHaveBeenCalledOnce();
        fixture.componentInstance.confirmExitDetailView(false);
        expect(emitCloseEditSpy).toHaveBeenCalledOnce();

        fixture.componentInstance.isSaved = false;
        fixture.componentInstance.confirmExitDetailView(true);
        expect(openModalSpy).toHaveBeenCalledOnce();
    });

    it('detectChanges', () => {
        const response: HttpResponse<ApollonDiagram> = new HttpResponse({ body: diagram });
        vi.spyOn(apollonDiagramService, 'find').mockReturnValue(of(response));
        vi.spyOn(courseService, 'find').mockReturnValue(of(new HttpResponse({ body: course })));

        fixture.detectChanges();
        expect(fixture.componentInstance.apollonDiagram()).toEqual(diagram);
        fixture.componentInstance.ngOnDestroy();
    });

    it('ngOnDestroy', () => {
        const response: HttpResponse<ApollonDiagram> = new HttpResponse({ body: diagram });
        vi.spyOn(apollonDiagramService, 'find').mockReturnValue(of(response));
        fixture.componentInstance.ngOnInit();
        expect(div.children).toHaveLength(0);

        // create spy after ngOnInit
        vi.spyOn(globalThis, 'clearInterval');

        // test
        fixture.componentInstance.ngOnDestroy();
        expect(div.children).toHaveLength(0);
        expect(clearInterval).toHaveBeenCalledOnce();
        expect(clearInterval).toHaveBeenCalledWith(fixture.componentInstance.autoSaveInterval);
    });
});
