/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { PointCounterDetailComponent } from 'app/entities/point-counter/point-counter-detail.component';
import { PointCounter } from 'app/shared/model/point-counter.model';

describe('Component Tests', () => {
    describe('PointCounter Management Detail Component', () => {
        let comp: PointCounterDetailComponent;
        let fixture: ComponentFixture<PointCounterDetailComponent>;
        const route = ({ data: of({ pointCounter: new PointCounter(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [PointCounterDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(PointCounterDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(PointCounterDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.pointCounter).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
