/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

import { ArTEMiSTestModule } from '../../../test.module';
import { MultipleChoiceQuestionStatisticDetailComponent } from '../../../../../../main/webapp/app/entities/multiple-choice-question-statistic/multiple-choice-question-statistic-detail.component';
import { MultipleChoiceQuestionStatisticService } from '../../../../../../main/webapp/app/entities/multiple-choice-question-statistic/multiple-choice-question-statistic.service';
import { MultipleChoiceQuestionStatistic } from '../../../../../../main/webapp/app/entities/multiple-choice-question-statistic/multiple-choice-question-statistic.model';

describe('Component Tests', () => {

    describe('MultipleChoiceQuestionStatistic Management Detail Component', () => {
        let comp: MultipleChoiceQuestionStatisticDetailComponent;
        let fixture: ComponentFixture<MultipleChoiceQuestionStatisticDetailComponent>;
        let service: MultipleChoiceQuestionStatisticService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [MultipleChoiceQuestionStatisticDetailComponent],
                providers: [
                    MultipleChoiceQuestionStatisticService
                ]
            })
            .overrideTemplate(MultipleChoiceQuestionStatisticDetailComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(MultipleChoiceQuestionStatisticDetailComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(MultipleChoiceQuestionStatisticService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                spyOn(service, 'find').and.returnValue(Observable.of(new HttpResponse({
                    body: new MultipleChoiceQuestionStatistic(123)
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.find).toHaveBeenCalledWith(123);
                expect(comp.multipleChoiceQuestionStatistic).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
