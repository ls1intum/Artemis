/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { QuestionStatisticUpdateComponent } from 'app/entities/question-statistic/question-statistic-update.component';
import { QuestionStatisticService } from 'app/entities/question-statistic/question-statistic.service';
import { QuestionStatistic } from 'app/shared/model/question-statistic.model';

describe('Component Tests', () => {
    describe('QuestionStatistic Management Update Component', () => {
        let comp: QuestionStatisticUpdateComponent;
        let fixture: ComponentFixture<QuestionStatisticUpdateComponent>;
        let service: QuestionStatisticService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [QuestionStatisticUpdateComponent]
            })
                .overrideTemplate(QuestionStatisticUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(QuestionStatisticUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(QuestionStatisticService);
        });

        describe('save', () => {
            it(
                'Should call update service on save for existing entity',
                fakeAsync(() => {
                    // GIVEN
                    const entity = new QuestionStatistic(123);
                    spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.questionStatistic = entity;
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
                    const entity = new QuestionStatistic();
                    spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.questionStatistic = entity;
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
