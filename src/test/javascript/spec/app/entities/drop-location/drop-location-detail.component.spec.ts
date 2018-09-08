/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { DropLocationDetailComponent } from 'app/entities/drop-location/drop-location-detail.component';
import { DropLocation } from 'app/shared/model/drop-location.model';

describe('Component Tests', () => {
    describe('DropLocation Management Detail Component', () => {
        let comp: DropLocationDetailComponent;
        let fixture: ComponentFixture<DropLocationDetailComponent>;
        const route = ({ data: of({ dropLocation: new DropLocation(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [DropLocationDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(DropLocationDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(DropLocationDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.dropLocation).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
