/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { StatisticCounterUpdateComponent } from 'app/entities/statistic-counter/statistic-counter-update.component';
import { StatisticCounterService } from 'app/entities/statistic-counter/statistic-counter.service';
import { StatisticCounter } from 'app/shared/model/statistic-counter.model';

describe('Component Tests', () => {
    describe('StatisticCounter Management Update Component', () => {
        let comp: StatisticCounterUpdateComponent;
        let fixture: ComponentFixture<StatisticCounterUpdateComponent>;
        let service: StatisticCounterService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [StatisticCounterUpdateComponent]
            })
                .overrideTemplate(StatisticCounterUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(StatisticCounterUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(StatisticCounterService);
        });

        describe('save', () => {
            it(
                'Should call update service on save for existing entity',
                fakeAsync(() => {
                    // GIVEN
                    const entity = new StatisticCounter(123);
                    spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.statisticCounter = entity;
                    // WHEN
                    comp.save();
                    tick(); // simulate async

                    // THEN
                    expect(service.update).toHaveBeenCalledWith(entity);
                    expect(comp.isSaving).toEqual(false);
                })
            );

            it(
                'Should call create service on save for new entity',
                fakeAsync(() => {
                    // GIVEN
                    const entity = new StatisticCounter();
                    spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.statisticCounter = entity;
                    // WHEN
                    comp.save();
                    tick(); // simulate async

                    // THEN
                    expect(service.create).toHaveBeenCalledWith(entity);
                    expect(comp.isSaving).toEqual(false);
                })
            );
        });
    });
});
