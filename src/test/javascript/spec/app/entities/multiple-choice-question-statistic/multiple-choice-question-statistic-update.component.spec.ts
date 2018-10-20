/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { MultipleChoiceQuestionStatisticUpdateComponent } from 'app/entities/multiple-choice-question-statistic/multiple-choice-question-statistic-update.component';
import { MultipleChoiceQuestionStatisticService } from 'app/entities/multiple-choice-question-statistic/multiple-choice-question-statistic.service';
import { MultipleChoiceQuestionStatistic } from 'app/shared/model/multiple-choice-question-statistic.model';

describe('Component Tests', () => {
    describe('MultipleChoiceQuestionStatistic Management Update Component', () => {
        let comp: MultipleChoiceQuestionStatisticUpdateComponent;
        let fixture: ComponentFixture<MultipleChoiceQuestionStatisticUpdateComponent>;
        let service: MultipleChoiceQuestionStatisticService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [MultipleChoiceQuestionStatisticUpdateComponent]
            })
                .overrideTemplate(MultipleChoiceQuestionStatisticUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(MultipleChoiceQuestionStatisticUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(MultipleChoiceQuestionStatisticService);
        });

        describe('save', () => {
            it('Should call update service on save for existing entity', fakeAsync(() => {
                // GIVEN
                const entity = new MultipleChoiceQuestionStatistic(123);
                spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.multipleChoiceQuestionStatistic = entity;
                // WHEN
                comp.save();
                tick(); // simulate async

                // THEN
                expect(service.update).toHaveBeenCalledWith(entity);
                expect(comp.isSaving).toEqual(false);
            }));

            it('Should call create service on save for new entity', fakeAsync(() => {
                // GIVEN
                const entity = new MultipleChoiceQuestionStatistic();
                spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.multipleChoiceQuestionStatistic = entity;
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
