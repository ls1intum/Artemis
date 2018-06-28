/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { Observable } from 'rxjs/Observable';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { DragAndDropAssignmentComponent } from '../../../../../../main/webapp/app/entities/drag-and-drop-assignment/drag-and-drop-assignment.component';
import { DragAndDropAssignmentService } from '../../../../../../main/webapp/app/entities/drag-and-drop-assignment/drag-and-drop-assignment.service';
import { DragAndDropAssignment } from '../../../../../../main/webapp/app/entities/drag-and-drop-assignment/drag-and-drop-assignment.model';

describe('Component Tests', () => {

    describe('DragAndDropAssignment Management Component', () => {
        let comp: DragAndDropAssignmentComponent;
        let fixture: ComponentFixture<DragAndDropAssignmentComponent>;
        let service: DragAndDropAssignmentService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [DragAndDropAssignmentComponent],
                providers: [
                    DragAndDropAssignmentService
                ]
            })
            .overrideTemplate(DragAndDropAssignmentComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(DragAndDropAssignmentComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(DragAndDropAssignmentService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN
                const headers = new HttpHeaders().append('link', 'link;link');
                spyOn(service, 'query').and.returnValue(Observable.of(new HttpResponse({
                    body: [new DragAndDropAssignment(123)],
                    headers
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.query).toHaveBeenCalled();
                expect(comp.dragAndDropAssignments[0]).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
