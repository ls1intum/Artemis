/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { MultipleChoiceQuestionStatisticComponent } from 'app/entities/multiple-choice-question-statistic/multiple-choice-question-statistic.component';
import { MultipleChoiceQuestionStatisticService } from 'app/entities/multiple-choice-question-statistic/multiple-choice-question-statistic.service';
import { MultipleChoiceQuestionStatistic } from 'app/shared/model/multiple-choice-question-statistic.model';

describe('Component Tests', () => {
    describe('MultipleChoiceQuestionStatistic Management Component', () => {
        let comp: MultipleChoiceQuestionStatisticComponent;
        let fixture: ComponentFixture<MultipleChoiceQuestionStatisticComponent>;
        let service: MultipleChoiceQuestionStatisticService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [MultipleChoiceQuestionStatisticComponent],
                providers: []
            })
                .overrideTemplate(MultipleChoiceQuestionStatisticComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(MultipleChoiceQuestionStatisticComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(MultipleChoiceQuestionStatisticService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new MultipleChoiceQuestionStatistic(123)],
                        headers
                    })
                )
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.multipleChoiceQuestionStatistics[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
