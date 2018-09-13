/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTEMiSTestModule } from '../../../test.module';
import { DragItemDetailComponent } from 'app/entities/drag-item/drag-item-detail.component';
import { DragItem } from 'app/shared/model/drag-item.model';

describe('Component Tests', () => {
    describe('DragItem Management Detail Component', () => {
        let comp: DragItemDetailComponent;
        let fixture: ComponentFixture<DragItemDetailComponent>;
        const route = ({ data: of({ dragItem: new DragItem(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [DragItemDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(DragItemDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(DragItemDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.dragItem).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
