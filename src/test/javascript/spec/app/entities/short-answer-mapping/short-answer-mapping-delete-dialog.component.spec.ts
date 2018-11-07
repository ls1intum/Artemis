/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, inject, fakeAsync, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable, of } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';

import { ArTeMiSTestModule } from '../../../test.module';
import { ShortAnswerMappingDeleteDialogComponent } from 'app/entities/short-answer-mapping/short-answer-mapping-delete-dialog.component';
import { ShortAnswerMappingService } from 'app/entities/short-answer-mapping/short-answer-mapping.service';

describe('Component Tests', () => {
    describe('ShortAnswerMapping Management Delete Component', () => {
        let comp: ShortAnswerMappingDeleteDialogComponent;
        let fixture: ComponentFixture<ShortAnswerMappingDeleteDialogComponent>;
        let service: ShortAnswerMappingService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ShortAnswerMappingDeleteDialogComponent]
            })
                .overrideTemplate(ShortAnswerMappingDeleteDialogComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(ShortAnswerMappingDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ShortAnswerMappingService);
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
