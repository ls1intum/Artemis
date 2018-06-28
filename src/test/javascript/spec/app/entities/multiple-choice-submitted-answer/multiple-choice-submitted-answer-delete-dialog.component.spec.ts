/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async, inject, fakeAsync, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable } from 'rxjs/Observable';
import { JhiEventManager } from 'ng-jhipster';

import { ArTeMiSTestModule } from '../../../test.module';
import { MultipleChoiceSubmittedAnswerDeleteDialogComponent } from '../../../../../../main/webapp/app/entities/multiple-choice-submitted-answer/multiple-choice-submitted-answer-delete-dialog.component';
import { MultipleChoiceSubmittedAnswerService } from '../../../../../../main/webapp/app/entities/multiple-choice-submitted-answer/multiple-choice-submitted-answer.service';

describe('Component Tests', () => {

    describe('MultipleChoiceSubmittedAnswer Management Delete Component', () => {
        let comp: MultipleChoiceSubmittedAnswerDeleteDialogComponent;
        let fixture: ComponentFixture<MultipleChoiceSubmittedAnswerDeleteDialogComponent>;
        let service: MultipleChoiceSubmittedAnswerService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [MultipleChoiceSubmittedAnswerDeleteDialogComponent],
                providers: [
                    MultipleChoiceSubmittedAnswerService
                ]
            })
            .overrideTemplate(MultipleChoiceSubmittedAnswerDeleteDialogComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(MultipleChoiceSubmittedAnswerDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(MultipleChoiceSubmittedAnswerService);
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
