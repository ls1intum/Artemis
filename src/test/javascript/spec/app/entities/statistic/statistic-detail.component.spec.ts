/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { StatisticDetailComponent } from 'app/entities/statistic/statistic-detail.component';
import { Statistic } from 'app/shared/model/statistic.model';

describe('Component Tests', () => {
    describe('Statistic Management Detail Component', () => {
        let comp: StatisticDetailComponent;
        let fixture: ComponentFixture<StatisticDetailComponent>;
        const route = ({ data: of({ statistic: new Statistic(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [StatisticDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(StatisticDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(StatisticDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.statistic).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
