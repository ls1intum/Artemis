/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { DragAndDropMappingDetailComponent } from 'app/entities/drag-and-drop-mapping/drag-and-drop-mapping-detail.component';
import { DragAndDropMapping } from 'app/shared/model/drag-and-drop-mapping.model';

describe('Component Tests', () => {
    describe('DragAndDropMapping Management Detail Component', () => {
        let comp: DragAndDropMappingDetailComponent;
        let fixture: ComponentFixture<DragAndDropMappingDetailComponent>;
        const route = ({ data: of({ dragAndDropMapping: new DragAndDropMapping(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [DragAndDropMappingDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(DragAndDropMappingDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(DragAndDropMappingDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.dragAndDropMapping).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
