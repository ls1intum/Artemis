/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { ShortAnswerQuestionStatisticUpdateComponent } from 'app/entities/short-answer-question-statistic/short-answer-question-statistic-update.component';
import { ShortAnswerQuestionStatisticService } from 'app/entities/short-answer-question-statistic/short-answer-question-statistic.service';
import { ShortAnswerQuestionStatistic } from 'app/entities/short-answer-question-statistic/short-answer-question-statistic.model';

describe('Component Tests', () => {
    describe('ShortAnswerQuestionStatistic Management Update Component', () => {
        let comp: ShortAnswerQuestionStatisticUpdateComponent;
        let fixture: ComponentFixture<ShortAnswerQuestionStatisticUpdateComponent>;
        let service: ShortAnswerQuestionStatisticService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ShortAnswerQuestionStatisticUpdateComponent]
            })
                .overrideTemplate(ShortAnswerQuestionStatisticUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(ShortAnswerQuestionStatisticUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ShortAnswerQuestionStatisticService);
        });

        describe('save', () => {
            it('Should call update service on save for existing entity', fakeAsync(() => {
                // GIVEN
                const entity = new ShortAnswerQuestionStatistic(123);
                spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.shortAnswerQuestionStatistic = entity;
                // WHEN
                comp.save();
                tick(); // simulate async

                // THEN
                expect(service.update).toHaveBeenCalledWith(entity);
                expect(comp.isSaving).toEqual(false);
            }));

            it('Should call create service on save for new entity', fakeAsync(() => {
                // GIVEN
                const entity = new ShortAnswerQuestionStatistic();
                spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.shortAnswerQuestionStatistic = entity;
                // WHEN
                comp.save();
                tick(); // simulate async

                // THEN
                expect(service.create).toHaveBeenCalledWith(entity);
                expect(comp.isSaving).toEqual(false);
            }));
        });
    });
});
