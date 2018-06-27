/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async, inject, fakeAsync, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable } from 'rxjs/Observable';
import { JhiEventManager } from 'ng-jhipster';

import { ArTEMiSTestModule } from '../../../test.module';
import { SubmittedAnswerDeleteDialogComponent } from '../../../../../../main/webapp/app/entities/submitted-answer/submitted-answer-delete-dialog.component';
import { SubmittedAnswerService } from '../../../../../../main/webapp/app/entities/submitted-answer/submitted-answer.service';

describe('Component Tests', () => {

    describe('SubmittedAnswer Management Delete Component', () => {
        let comp: SubmittedAnswerDeleteDialogComponent;
        let fixture: ComponentFixture<SubmittedAnswerDeleteDialogComponent>;
        let service: SubmittedAnswerService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [SubmittedAnswerDeleteDialogComponent],
                providers: [
                    SubmittedAnswerService
                ]
            })
            .overrideTemplate(SubmittedAnswerDeleteDialogComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(SubmittedAnswerDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(SubmittedAnswerService);
            mockEventManager = fixture.debugElement.injector.get(JhiEventManager);
            mockActiveModal = fixture.debugElement.injector.get(NgbActiveModal);
        });

        describe('confirmDelete', () => {
            it('Should call delete service on confirmDelete',
                inject([],
                    fakeAsync(() => {
                        // GIVEN
                        spyOn(service, 'delete').and.returnValue(Observable.of({}));

                        // WHEN
                        comp.confirmDelete(123);
                        tick();

                        // THEN
                        expect(service.delete).toHaveBeenCalledWith(123);
                        expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                        expect(mockEventManager.broadcastSpy).toHaveBeenCalled();
                    })
                )
            );
        });
    });

});
