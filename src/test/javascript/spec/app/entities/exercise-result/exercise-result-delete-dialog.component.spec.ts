/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, inject, fakeAsync, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable, of } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';

import { ArTeMiSTestModule } from '../../../test.module';
import { ExerciseResultDeleteDialogComponent } from 'app/entities/exercise-result/exercise-result-delete-dialog.component';
import { ExerciseResultService } from 'app/entities/exercise-result/exercise-result.service';

describe('Component Tests', () => {
    describe('ExerciseResult Management Delete Component', () => {
        let comp: ExerciseResultDeleteDialogComponent;
        let fixture: ComponentFixture<ExerciseResultDeleteDialogComponent>;
        let service: ExerciseResultService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ExerciseResultDeleteDialogComponent]
            })
                .overrideTemplate(ExerciseResultDeleteDialogComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(ExerciseResultDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ExerciseResultService);
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
