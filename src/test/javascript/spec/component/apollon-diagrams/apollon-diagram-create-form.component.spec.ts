import * as sinon from 'sinon';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { Router } from '@angular/router';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ApollonDiagram } from 'app/entities/apollon-diagram.model';
import { UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ApollonDiagramCreateFormComponent } from 'app/exercises/quiz/manage/apollon-diagrams/apollon-diagram-create-form.component';
import { ApollonDiagramService } from 'app/exercises/quiz/manage/apollon-diagrams/apollon-diagram.service';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';

describe('ApollonDiagramCreateForm Component', () => {
    let apollonDiagramService: ApollonDiagramService;
    let ngbModal: NgbActiveModal;
    let fixture: ComponentFixture<ApollonDiagramCreateFormComponent>;
    const sandbox = sinon.createSandbox();
    const diagram: ApollonDiagram = new ApollonDiagram(UMLDiagramType.ClassDiagram, 123);

    beforeEach(() => {
        diagram.id = 1;

        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            declarations: [ApollonDiagramCreateFormComponent],
            providers: [
                ApollonDiagramService,
                NgbActiveModal,
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
            ],
            schemas: [],
        })
            .overrideTemplate(ApollonDiagramCreateFormComponent, '')
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ApollonDiagramCreateFormComponent);
                apollonDiagramService = fixture.debugElement.injector.get(ApollonDiagramService);
                ngbModal = fixture.debugElement.injector.get(NgbActiveModal);
            });
    });

    afterEach(function () {
        sandbox.restore();
    });

    it('save', fakeAsync(() => {
        const response: HttpResponse<ApollonDiagram> = new HttpResponse({ body: diagram });
        sandbox.stub(apollonDiagramService, 'create').returns(of(response));
        const ngbModalSpy = sandbox.spy(ngbModal, 'dismiss');
        fixture.componentInstance.apollonDiagram = new ApollonDiagram(UMLDiagramType.ClassDiagram, 999);

        // test
        fixture.componentInstance.save();
        tick();
        expect(ngbModalSpy).toBeCalledTimes(1);
    }));
});
