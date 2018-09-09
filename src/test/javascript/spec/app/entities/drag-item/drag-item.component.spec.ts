/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTEMiSTestModule } from '../../../test.module';
import { DragItemComponent } from 'app/entities/drag-item/drag-item.component';
import { DragItemService } from 'app/entities/drag-item/drag-item.service';
import { DragItem } from 'app/shared/model/drag-item.model';

describe('Component Tests', () => {
    describe('DragItem Management Component', () => {
        let comp: DragItemComponent;
        let fixture: ComponentFixture<DragItemComponent>;
        let service: DragItemService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [DragItemComponent],
                providers: []
            })
                .overrideTemplate(DragItemComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(DragItemComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(DragItemService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new DragItem(123)],
                        headers
                    })
                )
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.dragItems[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
