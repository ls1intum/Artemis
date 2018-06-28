/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

import { ArTeMiSTestModule } from '../../../test.module';
import { DragItemDetailComponent } from '../../../../../../main/webapp/app/entities/drag-item/drag-item-detail.component';
import { DragItemService } from '../../../../../../main/webapp/app/entities/drag-item/drag-item.service';
import { DragItem } from '../../../../../../main/webapp/app/entities/drag-item/drag-item.model';

describe('Component Tests', () => {

    describe('DragItem Management Detail Component', () => {
        let comp: DragItemDetailComponent;
        let fixture: ComponentFixture<DragItemDetailComponent>;
        let service: DragItemService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [DragItemDetailComponent],
                providers: [
                    DragItemService
                ]
            })
            .overrideTemplate(DragItemDetailComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(DragItemDetailComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(DragItemService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                spyOn(service, 'find').and.returnValue(Observable.of(new HttpResponse({
                    body: new DragItem(123)
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.find).toHaveBeenCalledWith(123);
                expect(comp.dragItem).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
