/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { QuestionStatisticDetailComponent } from 'app/entities/question-statistic/question-statistic-detail.component';
import { QuestionStatistic } from 'app/shared/model/question-statistic.model';

describe('Component Tests', () => {
    describe('QuestionStatistic Management Detail Component', () => {
        let comp: QuestionStatisticDetailComponent;
        let fixture: ComponentFixture<QuestionStatisticDetailComponent>;
        const route = ({ data: of({ questionStatistic: new QuestionStatistic(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [QuestionStatisticDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(QuestionStatisticDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(QuestionStatisticDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.questionStatistic).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
