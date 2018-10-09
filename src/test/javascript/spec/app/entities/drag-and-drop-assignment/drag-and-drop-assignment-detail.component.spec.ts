/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTEMiSTestModule } from '../../../test.module';
import { DragAndDropAssignmentDetailComponent } from 'app/entities/drag-and-drop-assignment/drag-and-drop-assignment-detail.component';
import { DragAndDropAssignment } from 'app/shared/model/drag-and-drop-assignment.model';

describe('Component Tests', () => {
    describe('DragAndDropAssignment Management Detail Component', () => {
        let comp: DragAndDropAssignmentDetailComponent;
        let fixture: ComponentFixture<DragAndDropAssignmentDetailComponent>;
        const route = ({ data: of({ dragAndDropAssignment: new DragAndDropAssignment(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [DragAndDropAssignmentDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(DragAndDropAssignmentDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(DragAndDropAssignmentDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.dragAndDropAssignment).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
