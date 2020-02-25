/* tslint:disable max-line-length */
import { ComponentFixture, fakeAsync, inject, TestBed, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { of } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';

import { ArtemisTestModule } from '../../../test.module';
import { ModelingExerciseDeleteDialogComponent } from 'app/exercises/modeling/manage/modeling-exercise/modeling-exercise-delete-dialog.component';
import { ModelingExerciseService } from 'app/exercises/modeling/manage/modeling-exercise/modeling-exercise.service';

describe('Component Tests', () => {
    describe('ModelingExercise Management Delete Component', () => {
        let comp: ModelingExerciseDeleteDialogComponent;
        let fixture: ComponentFixture<ModelingExerciseDeleteDialogComponent>;
        let service: ModelingExerciseService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArtemisTestModule],
                declarations: [ModelingExerciseDeleteDialogComponent],
            })
                .overrideTemplate(ModelingExerciseDeleteDialogComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(ModelingExerciseDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ModelingExerciseService);
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
