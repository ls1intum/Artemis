/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { Observable } from 'rxjs/Observable';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { DragItemComponent } from '../../../../../../main/webapp/app/entities/drag-item/drag-item.component';
import { DragItemService } from '../../../../../../main/webapp/app/entities/drag-item/drag-item.service';
import { DragItem } from '../../../../../../main/webapp/app/entities/drag-item/drag-item.model';

describe('Component Tests', () => {

    describe('DragItem Management Component', () => {
        let comp: DragItemComponent;
        let fixture: ComponentFixture<DragItemComponent>;
        let service: DragItemService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [DragItemComponent],
                providers: [
                    DragItemService
                ]
            })
            .overrideTemplate(DragItemComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(DragItemComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(DragItemService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN
                const headers = new HttpHeaders().append('link', 'link;link');
                spyOn(service, 'query').and.returnValue(Observable.of(new HttpResponse({
                    body: [new DragItem(123)],
                    headers
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.query).toHaveBeenCalled();
                expect(comp.dragItems[0]).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
