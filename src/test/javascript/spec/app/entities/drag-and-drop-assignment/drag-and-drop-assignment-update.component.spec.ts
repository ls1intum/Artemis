/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { DragAndDropAssignmentUpdateComponent } from 'app/entities/drag-and-drop-assignment/drag-and-drop-assignment-update.component';
import { DragAndDropAssignmentService } from 'app/entities/drag-and-drop-assignment/drag-and-drop-assignment.service';
import { DragAndDropAssignment } from 'app/shared/model/drag-and-drop-assignment.model';

describe('Component Tests', () => {
    describe('DragAndDropAssignment Management Update Component', () => {
        let comp: DragAndDropAssignmentUpdateComponent;
        let fixture: ComponentFixture<DragAndDropAssignmentUpdateComponent>;
        let service: DragAndDropAssignmentService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [DragAndDropAssignmentUpdateComponent]
            })
                .overrideTemplate(DragAndDropAssignmentUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(DragAndDropAssignmentUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(DragAndDropAssignmentService);
        });

        describe('save', () => {
            it(
                'Should call update service on save for existing entity',
                fakeAsync(() => {
                    // GIVEN
                    const entity = new DragAndDropAssignment(123);
                    spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.dragAndDropAssignment = entity;
                    // WHEN
                    comp.save();
                    tick(); // simulate async

                    // THEN
                    expect(service.update).toHaveBeenCalledWith(entity);
                    expect(comp.isSaving).toEqual(false);
                })
            );

            it(
                'Should call create service on save for new entity',
                fakeAsync(() => {
                    // GIVEN
                    const entity = new DragAndDropAssignment();
                    spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.dragAndDropAssignment = entity;
                    // WHEN
                    comp.save();
                    tick(); // simulate async

                    // THEN
                    expect(service.create).toHaveBeenCalledWith(entity);
                    expect(comp.isSaving).toEqual(false);
                })
            );
        });
    });
});
