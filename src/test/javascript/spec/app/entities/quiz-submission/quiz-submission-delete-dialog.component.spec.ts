/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async, inject, fakeAsync, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable } from 'rxjs/Observable';
import { JhiEventManager } from 'ng-jhipster';

import { ArTeMiSTestModule } from '../../../test.module';
import { QuizSubmissionDeleteDialogComponent } from '../../../../../../main/webapp/app/entities/quiz-submission/quiz-submission-delete-dialog.component';
import { QuizSubmissionService } from '../../../../../../main/webapp/app/entities/quiz-submission/quiz-submission.service';

describe('Component Tests', () => {

    describe('QuizSubmission Management Delete Component', () => {
        let comp: QuizSubmissionDeleteDialogComponent;
        let fixture: ComponentFixture<QuizSubmissionDeleteDialogComponent>;
        let service: QuizSubmissionService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [QuizSubmissionDeleteDialogComponent],
                providers: [
                    QuizSubmissionService
                ]
            })
            .overrideTemplate(QuizSubmissionDeleteDialogComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(QuizSubmissionDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(QuizSubmissionService);
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
