/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

import { ArTeMiSTestModule } from '../../../test.module';
import { DragAndDropAssignmentDetailComponent } from '../../../../../../main/webapp/app/entities/drag-and-drop-assignment/drag-and-drop-assignment-detail.component';
import { DragAndDropAssignmentService } from '../../../../../../main/webapp/app/entities/drag-and-drop-assignment/drag-and-drop-assignment.service';
import { DragAndDropAssignment } from '../../../../../../main/webapp/app/entities/drag-and-drop-assignment/drag-and-drop-assignment.model';

describe('Component Tests', () => {

    describe('DragAndDropAssignment Management Detail Component', () => {
        let comp: DragAndDropAssignmentDetailComponent;
        let fixture: ComponentFixture<DragAndDropAssignmentDetailComponent>;
        let service: DragAndDropAssignmentService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [DragAndDropAssignmentDetailComponent],
                providers: [
                    DragAndDropAssignmentService
                ]
            })
            .overrideTemplate(DragAndDropAssignmentDetailComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(DragAndDropAssignmentDetailComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(DragAndDropAssignmentService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                spyOn(service, 'find').and.returnValue(Observable.of(new HttpResponse({
                    body: new DragAndDropAssignment(123)
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.find).toHaveBeenCalledWith(123);
                expect(comp.dragAndDropAssignment).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
