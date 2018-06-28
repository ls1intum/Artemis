/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async, inject, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable } from 'rxjs/Observable';
import { JhiEventManager } from 'ng-jhipster';

import { ArTeMiSTestModule } from '../../../test.module';
import { ProgrammingExerciseDialogComponent } from '../../../../../../main/webapp/app/entities/programming-exercise/programming-exercise-dialog.component';
import { ProgrammingExerciseService } from '../../../../../../main/webapp/app/entities/programming-exercise/programming-exercise.service';
import { ProgrammingExercise } from '../../../../../../main/webapp/app/entities/programming-exercise/programming-exercise.model';

describe('Component Tests', () => {

    describe('ProgrammingExercise Management Dialog Component', () => {
        let comp: ProgrammingExerciseDialogComponent;
        let fixture: ComponentFixture<ProgrammingExerciseDialogComponent>;
        let service: ProgrammingExerciseService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ProgrammingExerciseDialogComponent],
                providers: [
                    ProgrammingExerciseService
                ]
            })
            .overrideTemplate(ProgrammingExerciseDialogComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(ProgrammingExerciseDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ProgrammingExerciseService);
            mockEventManager = fixture.debugElement.injector.get(JhiEventManager);
            mockActiveModal = fixture.debugElement.injector.get(NgbActiveModal);
        });

        describe('save', () => {
            it('Should call update service on save for existing entity',
                inject([],
                    fakeAsync(() => {
                        // GIVEN
                        const entity = new ProgrammingExercise(123);
                        spyOn(service, 'update').and.returnValue(Observable.of(new HttpResponse({body: entity})));
                        comp.programmingExercise = entity;
                        // WHEN
                        comp.save();
                        tick(); // simulate async

                        // THEN
                        expect(service.update).toHaveBeenCalledWith(entity);
                        expect(comp.isSaving).toEqual(false);
                        expect(mockEventManager.broadcastSpy).toHaveBeenCalledWith({ name: 'programmingExerciseListModification', content: 'OK'});
                        expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                    })
                )
            );

            it('Should call create service on save for new entity',
                inject([],
                    fakeAsync(() => {
                        // GIVEN
                        const entity = new ProgrammingExercise();
                        spyOn(service, 'create').and.returnValue(Observable.of(new HttpResponse({body: entity})));
                        comp.programmingExercise = entity;
                        // WHEN
                        comp.save();
                        tick(); // simulate async

                        // THEN
                        expect(service.create).toHaveBeenCalledWith(entity);
                        expect(comp.isSaving).toEqual(false);
                        expect(mockEventManager.broadcastSpy).toHaveBeenCalledWith({ name: 'programmingExerciseListModification', content: 'OK'});
                        expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                    })
                )
            );
        });
    });

});
