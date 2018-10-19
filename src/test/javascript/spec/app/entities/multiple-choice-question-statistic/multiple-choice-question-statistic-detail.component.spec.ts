/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { MultipleChoiceQuestionStatisticDetailComponent } from 'app/entities/multiple-choice-question-statistic/multiple-choice-question-statistic-detail.component';
import { MultipleChoiceQuestionStatistic } from 'app/shared/model/multiple-choice-question-statistic.model';

describe('Component Tests', () => {
    describe('MultipleChoiceQuestionStatistic Management Detail Component', () => {
        let comp: MultipleChoiceQuestionStatisticDetailComponent;
        let fixture: ComponentFixture<MultipleChoiceQuestionStatisticDetailComponent>;
        const route = ({
            data: of({ multipleChoiceQuestionStatistic: new MultipleChoiceQuestionStatistic(123) })
        } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [MultipleChoiceQuestionStatisticDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(MultipleChoiceQuestionStatisticDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(MultipleChoiceQuestionStatisticDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.multipleChoiceQuestionStatistic).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
