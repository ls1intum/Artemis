/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTEMiSTestModule } from '../../../test.module';
import { DragAndDropAssignmentComponent } from 'app/entities/drag-and-drop-assignment/drag-and-drop-assignment.component';
import { DragAndDropAssignmentService } from 'app/entities/drag-and-drop-assignment/drag-and-drop-assignment.service';
import { DragAndDropAssignment } from 'app/shared/model/drag-and-drop-assignment.model';

describe('Component Tests', () => {
    describe('DragAndDropAssignment Management Component', () => {
        let comp: DragAndDropAssignmentComponent;
        let fixture: ComponentFixture<DragAndDropAssignmentComponent>;
        let service: DragAndDropAssignmentService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [DragAndDropAssignmentComponent],
                providers: []
            })
                .overrideTemplate(DragAndDropAssignmentComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(DragAndDropAssignmentComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(DragAndDropAssignmentService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new DragAndDropAssignment(123)],
                        headers
                    })
                )
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.dragAndDropAssignments[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
