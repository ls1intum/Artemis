/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { QuizPointStatisticUpdateComponent } from 'app/entities/quiz-point-statistic/quiz-point-statistic-update.component';
import { QuizPointStatisticService } from 'app/entities/quiz-point-statistic/quiz-point-statistic.service';
import { QuizPointStatistic } from 'app/shared/model/quiz-point-statistic.model';

describe('Component Tests', () => {
    describe('QuizPointStatistic Management Update Component', () => {
        let comp: QuizPointStatisticUpdateComponent;
        let fixture: ComponentFixture<QuizPointStatisticUpdateComponent>;
        let service: QuizPointStatisticService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [QuizPointStatisticUpdateComponent]
            })
                .overrideTemplate(QuizPointStatisticUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(QuizPointStatisticUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(QuizPointStatisticService);
        });

        describe('save', () => {
            it(
                'Should call update service on save for existing entity',
                fakeAsync(() => {
                    // GIVEN
                    const entity = new QuizPointStatistic(123);
                    spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.quizPointStatistic = entity;
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
                    const entity = new QuizPointStatistic();
                    spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.quizPointStatistic = entity;
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
