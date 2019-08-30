/* tslint:disable max-line-length */
import { ComponentFixture, fakeAsync, inject, TestBed, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { of } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';

import { ArtemisTestModule } from '../../../test.module';
import { QuestionDeleteDialogComponent } from 'app/entities/quiz-question/question-delete-dialog.component';
import { QuestionService } from 'app/entities/quiz-question/question.service';

describe('Component Tests', () => {
    describe('Question Management Delete Component', () => {
        let comp: QuestionDeleteDialogComponent;
        let fixture: ComponentFixture<QuestionDeleteDialogComponent>;
        let service: QuestionService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArtemisTestModule],
                declarations: [QuestionDeleteDialogComponent],
            })
                .overrideTemplate(QuestionDeleteDialogComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(QuestionDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(QuestionService);
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
                }),
            ));
        });
    });
});
