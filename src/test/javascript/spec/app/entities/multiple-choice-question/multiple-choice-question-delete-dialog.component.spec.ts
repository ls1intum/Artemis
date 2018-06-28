/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async, inject, fakeAsync, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable } from 'rxjs/Observable';
import { JhiEventManager } from 'ng-jhipster';

import { ArTeMiSTestModule } from '../../../test.module';
import { MultipleChoiceQuestionDeleteDialogComponent } from '../../../../../../main/webapp/app/entities/multiple-choice-question/multiple-choice-question-delete-dialog.component';
import { MultipleChoiceQuestionService } from '../../../../../../main/webapp/app/entities/multiple-choice-question/multiple-choice-question.service';

describe('Component Tests', () => {

    describe('MultipleChoiceQuestion Management Delete Component', () => {
        let comp: MultipleChoiceQuestionDeleteDialogComponent;
        let fixture: ComponentFixture<MultipleChoiceQuestionDeleteDialogComponent>;
        let service: MultipleChoiceQuestionService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [MultipleChoiceQuestionDeleteDialogComponent],
                providers: [
                    MultipleChoiceQuestionService
                ]
            })
            .overrideTemplate(MultipleChoiceQuestionDeleteDialogComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(MultipleChoiceQuestionDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(MultipleChoiceQuestionService);
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
