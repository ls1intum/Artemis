/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, inject, fakeAsync, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable, of } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';

import { ArTeMiSTestModule } from '../../../test.module';
import { TextExerciseDeleteDialogComponent } from 'app/entities/text-exercise/text-exercise-delete-dialog.component';
import { TextExerciseService } from 'app/entities/text-exercise/text-exercise.service';

describe('Component Tests', () => {
    describe('TextExercise Management Delete Component', () => {
        let comp: TextExerciseDeleteDialogComponent;
        let fixture: ComponentFixture<TextExerciseDeleteDialogComponent>;
        let service: TextExerciseService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [TextExerciseDeleteDialogComponent]
            })
                .overrideTemplate(TextExerciseDeleteDialogComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(TextExerciseDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(TextExerciseService);
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
