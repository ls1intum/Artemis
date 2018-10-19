/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { StatisticUpdateComponent } from 'app/entities/statistic/statistic-update.component';
import { StatisticService } from 'app/entities/statistic/statistic.service';
import { Statistic } from 'app/shared/model/statistic.model';

describe('Component Tests', () => {
    describe('Statistic Management Update Component', () => {
        let comp: StatisticUpdateComponent;
        let fixture: ComponentFixture<StatisticUpdateComponent>;
        let service: StatisticService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [StatisticUpdateComponent]
            })
                .overrideTemplate(StatisticUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(StatisticUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(StatisticService);
        });

        describe('save', () => {
            it(
                'Should call update service on save for existing entity',
                fakeAsync(() => {
                    // GIVEN
                    const entity = new Statistic(123);
                    spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.statistic = entity;
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
                    const entity = new Statistic();
                    spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.statistic = entity;
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
