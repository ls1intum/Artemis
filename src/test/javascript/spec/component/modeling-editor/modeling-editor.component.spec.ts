import { Course } from 'app/entities/course.model';
import * as sinon from 'sinon';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { of } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';
import { ApollonDiagram } from 'app/entities/apollon-diagram.model';
import { UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { MockRouter } from '../../helpers/mocks/service/mock-route.service';
import { UMLModel } from '@ls1intum/apollon';
import { Text } from '@ls1intum/apollon/lib/utils/svg/text';
import { ModelingEditorComponent } from 'app/exercises/modeling/shared/modeling-editor.component';
import * as testClassDiagram from '../../util/modeling/test-models/class-diagram.json';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { ArtemisModelingEditorModule } from 'app/exercises/modeling/shared/modeling-editor.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ArtemisTestModule } from '../../test.module';
import { cloneDeep } from 'lodash';
import { SimpleChange } from '@angular/core';

// has to be overridden, because jsdom does not provide a getBBox() function for SVGTextElements
Text.size = () => {
    return { width: 0, height: 0 };
};

describe('ModelingEditorComponent Component', () => {
    let fixture: ComponentFixture<ModelingEditorComponent>;
    const sandbox = sinon.createSandbox();
    const course: Course = { id: 123 } as Course;
    const diagram: ApollonDiagram = new ApollonDiagram(UMLDiagramType.ClassDiagram, course.id!);

    beforeEach(() => {
        const route = { params: of({ id: 1, courseId: 123 }), snapshot: { paramMap: convertToParamMap({ courseId: course.id }) } } as any as ActivatedRoute;
        diagram.id = 1;
        diagram.jsonRepresentation = JSON.stringify(testClassDiagram);

        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule, ArtemisTestModule, ArtemisSharedModule, ArtemisModelingEditorModule],
            declarations: [],
            providers: [
                AlertService,
                JhiLanguageHelper,
                GuidedTourService,
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useValue: MockRouter },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ModelingEditorComponent);
            });
    });

    afterEach(function () {
        sandbox.restore();
    });

    it('ngAfterViewInit', () => {
        // @ts-ignore
        fixture.componentInstance.umlModel = testClassDiagram as UMLModel;
        fixture.detectChanges();

        // test
        fixture.componentInstance.ngAfterViewInit();
        expect(fixture.componentInstance['apollonEditor']).toBeTruthy();
    });

    it('ngOnDestroy', () => {
        // @ts-ignore
        fixture.componentInstance.umlModel = testClassDiagram as UMLModel;
        fixture.detectChanges();
        fixture.componentInstance.ngAfterViewInit();
        expect(fixture.componentInstance['apollonEditor']).toBeTruthy();

        // test
        fixture.componentInstance.ngOnDestroy();
        expect(fixture.componentInstance['apollonEditor']).toBeFalsy();
    });

    it('ngOnChanges', () => {
        // @ts-ignore
        const model = testClassDiagram as UMLModel;
        fixture.componentInstance.umlModel = model;
        fixture.detectChanges();
        fixture.componentInstance.ngAfterViewInit();
        expect(fixture.componentInstance['apollonEditor']).toBeTruthy();

        const changedModel = cloneDeep(model);
        changedModel.elements = [];
        changedModel.relationships = [];
        changedModel.interactive = { elements: [], relationships: [] };
        changedModel.size = { height: 0, width: 0 };

        // test
        fixture.componentInstance.ngOnChanges({
            umlModel: {
                currentValue: changedModel,
                previousValue: model,
            } as SimpleChange,
        });

        expect(fixture.componentInstance['apollonEditor']!.model).toEqual(changedModel);
    });

    it('isFullScreen false', () => {
        // test
        const fullScreen = fixture.componentInstance.isFullScreen;
        expect(fullScreen).toBeFalsy();
    });

    it('getCurrentModel', () => {
        // @ts-ignore
        fixture.componentInstance.umlModel = testClassDiagram as UMLModel;
        fixture.detectChanges();
        fixture.componentInstance.ngAfterViewInit();
        expect(fixture.componentInstance['apollonEditor']).toBeTruthy();

        // test
        // const model = fixture.componentInstance.getCurrentModel();
        // TODO: uncomment after deserialization bugfix in Apollon library, see https://github.com/ls1intum/Apollon/issues/146
        // expect(model).toEqual(testClassDiagram);
    });

    it('elementWithClass', () => {
        // @ts-ignore
        const model = testClassDiagram as UMLModel;
        fixture.componentInstance.umlModel = model;
        fixture.detectChanges();
        fixture.componentInstance.ngAfterViewInit();
        expect(fixture.componentInstance['apollonEditor']).toBeTruthy();

        // test
        const umlElement = fixture.componentInstance.elementWithClass('Sibling 2', model);
        expect(umlElement).toBeTruthy();
    });

    it('elementWithAttribute', () => {
        // @ts-ignore
        const model = testClassDiagram as UMLModel;
        fixture.componentInstance.umlModel = model;
        fixture.detectChanges();
        fixture.componentInstance.ngAfterViewInit();
        expect(fixture.componentInstance['apollonEditor']).toBeTruthy();

        // test
        const umlElement = fixture.componentInstance.elementWithAttribute('attribute', model);
        expect(umlElement).toBeTruthy();
    });

    it('elementWithMethod', () => {
        // @ts-ignore
        const model = testClassDiagram as UMLModel;
        fixture.componentInstance.umlModel = model;
        fixture.detectChanges();
        fixture.componentInstance.ngAfterViewInit();
        expect(fixture.componentInstance['apollonEditor']).toBeTruthy();

        // test
        const umlElement = fixture.componentInstance.elementWithMethod('method', model);
        expect(umlElement).toBeTruthy();
    });
});
