/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { Observable } from 'rxjs/Observable';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTEMiSTestModule } from '../../../test.module';
import { MultipleChoiceQuestionStatisticComponent } from '../../../../../../main/webapp/app/entities/multiple-choice-question-statistic/multiple-choice-question-statistic.component';
import { MultipleChoiceQuestionStatisticService } from '../../../../../../main/webapp/app/entities/multiple-choice-question-statistic/multiple-choice-question-statistic.service';
import { MultipleChoiceQuestionStatistic } from '../../../../../../main/webapp/app/entities/multiple-choice-question-statistic/multiple-choice-question-statistic.model';

describe('Component Tests', () => {

    describe('MultipleChoiceQuestionStatistic Management Component', () => {
        let comp: MultipleChoiceQuestionStatisticComponent;
        let fixture: ComponentFixture<MultipleChoiceQuestionStatisticComponent>;
        let service: MultipleChoiceQuestionStatisticService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [MultipleChoiceQuestionStatisticComponent],
                providers: [
                    MultipleChoiceQuestionStatisticService
                ]
            })
            .overrideTemplate(MultipleChoiceQuestionStatisticComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(MultipleChoiceQuestionStatisticComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(MultipleChoiceQuestionStatisticService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN
                const headers = new HttpHeaders().append('link', 'link;link');
                spyOn(service, 'query').and.returnValue(Observable.of(new HttpResponse({
                    body: [new MultipleChoiceQuestionStatistic(123)],
                    headers
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.query).toHaveBeenCalled();
                expect(comp.multipleChoiceQuestionStatistics[0]).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
