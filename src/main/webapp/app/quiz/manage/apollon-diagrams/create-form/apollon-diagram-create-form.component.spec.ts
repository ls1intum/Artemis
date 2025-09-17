import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Router } from '@angular/router';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ApollonDiagram } from 'app/modeling/shared/entities/apollon-diagram.model';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { ApollonDiagramCreateFormComponent } from 'app/quiz/manage/apollon-diagrams/create-form/apollon-diagram-create-form.component';
import { ApollonDiagramService } from 'app/quiz/manage/apollon-diagrams/services/apollon-diagram.service';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockTranslateService } from 'src/test/javascript/spec/helpers/mocks/service/mock-translate.service';
import { MockRouter } from 'src/test/javascript/spec/helpers/mocks/mock-router';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { of } from 'rxjs';
import { UMLDiagramType } from '@tumaet/apollon';

describe('ApollonDiagramCreateForm Component', () => {
    let apollonDiagramService: ApollonDiagramService;
    let ngbModal: NgbActiveModal;
    let fixture: ComponentFixture<ApollonDiagramCreateFormComponent>;

    const diagram: ApollonDiagram = new ApollonDiagram(UMLDiagramType.ClassDiagram, 123);

    beforeEach(() => {
        diagram.id = 1;

        TestBed.configureTestingModule({
            imports: [ApollonDiagramCreateFormComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                ApollonDiagramService,
                NgbActiveModal,
                SessionStorageService,
                LocalStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
            ],
        })
            .overrideTemplate(ApollonDiagramCreateFormComponent, '')
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ApollonDiagramCreateFormComponent);
                apollonDiagramService = fixture.debugElement.injector.get(ApollonDiagramService);
                ngbModal = fixture.debugElement.injector.get(NgbActiveModal);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('save', fakeAsync(() => {
        const response: HttpResponse<ApollonDiagram> = new HttpResponse({ body: diagram });
        jest.spyOn(apollonDiagramService, 'create').mockReturnValue(of(response));
        const ngbModalSpy = jest.spyOn(ngbModal, 'close');
        fixture.componentInstance.apollonDiagram = new ApollonDiagram(UMLDiagramType.ClassDiagram, 999);

        // test
        fixture.componentInstance.save();
        tick();
        expect(ngbModalSpy).toHaveBeenCalledOnce();
    }));
});
