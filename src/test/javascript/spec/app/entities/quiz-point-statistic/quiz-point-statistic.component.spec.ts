/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { QuizPointStatisticComponent } from 'app/entities/quiz-point-statistic/quiz-point-statistic.component';
import { QuizPointStatisticService } from 'app/entities/quiz-point-statistic/quiz-point-statistic.service';
import { QuizPointStatistic } from 'app/shared/model/quiz-point-statistic.model';

describe('Component Tests', () => {
    describe('QuizPointStatistic Management Component', () => {
        let comp: QuizPointStatisticComponent;
        let fixture: ComponentFixture<QuizPointStatisticComponent>;
        let service: QuizPointStatisticService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [QuizPointStatisticComponent],
                providers: []
            })
                .overrideTemplate(QuizPointStatisticComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(QuizPointStatisticComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(QuizPointStatisticService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new QuizPointStatistic(123)],
                        headers
                    })
                )
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.quizPointStatistics[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
