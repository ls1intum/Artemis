/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, inject, fakeAsync, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable, of } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';

import { ArTeMiSTestModule } from '../../../test.module';
import { ShortAnswerSpotDeleteDialogComponent } from 'app/entities/short-answer-spot/short-answer-spot-delete-dialog.component';
import { ShortAnswerSpotService } from 'app/entities/short-answer-spot/short-answer-spot.service';

describe('Component Tests', () => {
    describe('ShortAnswerSpot Management Delete Component', () => {
        let comp: ShortAnswerSpotDeleteDialogComponent;
        let fixture: ComponentFixture<ShortAnswerSpotDeleteDialogComponent>;
        let service: ShortAnswerSpotService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ShortAnswerSpotDeleteDialogComponent]
            })
                .overrideTemplate(ShortAnswerSpotDeleteDialogComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(ShortAnswerSpotDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ShortAnswerSpotService);
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
