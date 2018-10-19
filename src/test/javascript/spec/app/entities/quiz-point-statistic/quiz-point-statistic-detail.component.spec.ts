/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { QuizPointStatisticDetailComponent } from 'app/entities/quiz-point-statistic/quiz-point-statistic-detail.component';
import { QuizPointStatistic } from 'app/shared/model/quiz-point-statistic.model';

describe('Component Tests', () => {
    describe('QuizPointStatistic Management Detail Component', () => {
        let comp: QuizPointStatisticDetailComponent;
        let fixture: ComponentFixture<QuizPointStatisticDetailComponent>;
        const route = ({ data: of({ quizPointStatistic: new QuizPointStatistic(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [QuizPointStatisticDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(QuizPointStatisticDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(QuizPointStatisticDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.quizPointStatistic).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
