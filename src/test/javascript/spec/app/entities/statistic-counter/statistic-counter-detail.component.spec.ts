/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { StatisticCounterDetailComponent } from 'app/entities/statistic-counter/statistic-counter-detail.component';
import { StatisticCounter } from 'app/shared/model/statistic-counter.model';

describe('Component Tests', () => {
    describe('StatisticCounter Management Detail Component', () => {
        let comp: StatisticCounterDetailComponent;
        let fixture: ComponentFixture<StatisticCounterDetailComponent>;
        const route = ({ data: of({ statisticCounter: new StatisticCounter(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [StatisticCounterDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(StatisticCounterDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(StatisticCounterDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.statisticCounter).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
