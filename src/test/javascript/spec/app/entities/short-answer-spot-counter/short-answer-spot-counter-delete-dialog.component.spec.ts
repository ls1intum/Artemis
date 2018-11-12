/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, inject, fakeAsync, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable, of } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';

import { ArTeMiSTestModule } from '../../../test.module';
import { ShortAnswerSpotCounterDeleteDialogComponent } from 'app/entities/short-answer-spot-counter/short-answer-spot-counter-delete-dialog.component';
import { ShortAnswerSpotCounterService } from 'app/entities/short-answer-spot-counter/short-answer-spot-counter.service';

describe('Component Tests', () => {
    describe('ShortAnswerSpotCounter Management Delete Component', () => {
        let comp: ShortAnswerSpotCounterDeleteDialogComponent;
        let fixture: ComponentFixture<ShortAnswerSpotCounterDeleteDialogComponent>;
        let service: ShortAnswerSpotCounterService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ShortAnswerSpotCounterDeleteDialogComponent]
            })
                .overrideTemplate(ShortAnswerSpotCounterDeleteDialogComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(ShortAnswerSpotCounterDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ShortAnswerSpotCounterService);
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
