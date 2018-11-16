/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, inject, fakeAsync, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable, of } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';

import { ArTeMiSTestModule } from '../../../test.module';
import { ApollonDiagramDeleteDialogComponent } from 'app/entities/apollon-diagram/apollon-diagram-delete-dialog.component';
import { ApollonDiagramService } from 'app/entities/apollon-diagram/apollon-diagram.service';

describe('Component Tests', () => {
    describe('ApollonDiagram Management Delete Component', () => {
        let comp: ApollonDiagramDeleteDialogComponent;
        let fixture: ComponentFixture<ApollonDiagramDeleteDialogComponent>;
        let service: ApollonDiagramService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ApollonDiagramDeleteDialogComponent]
            })
                .overrideTemplate(ApollonDiagramDeleteDialogComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(ApollonDiagramDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ApollonDiagramService);
            mockEventManager = fixture.debugElement.injector.get(JhiEventManager);
            mockActiveModal = fixture.debugElement.injector.get(NgbActiveModal);
        });

        describe('confirmDelete', () => {
            it('Should call delete service on confirmDelete', inject(
                [],
                fakeAsync(() => {
                    // GIVEN
                    spyOn(service, 'delete').and.returnValue(of({}));

                    // WHEN
                    comp.confirmDelete(123);
                    tick();

                    // THEN
                    expect(service.delete).toHaveBeenCalledWith(123);
                    expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                    expect(mockEventManager.broadcastSpy).toHaveBeenCalled();
                })
            ));
        });
    });
});
