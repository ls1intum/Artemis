/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

import { ArTEMiSTestModule } from '../../../test.module';
import { QuestionStatisticDetailComponent } from '../../../../../../main/webapp/app/entities/question-statistic/question-statistic-detail.component';
import { QuestionStatisticService } from '../../../../../../main/webapp/app/entities/question-statistic/question-statistic.service';
import { QuestionStatistic } from '../../../../../../main/webapp/app/entities/question-statistic/question-statistic.model';

describe('Component Tests', () => {

    describe('QuestionStatistic Management Detail Component', () => {
        let comp: QuestionStatisticDetailComponent;
        let fixture: ComponentFixture<QuestionStatisticDetailComponent>;
        let service: QuestionStatisticService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [QuestionStatisticDetailComponent],
                providers: [
                    QuestionStatisticService
                ]
            })
            .overrideTemplate(QuestionStatisticDetailComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(QuestionStatisticDetailComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(QuestionStatisticService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                spyOn(service, 'find').and.returnValue(Observable.of(new HttpResponse({
                    body: new QuestionStatistic(123)
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.find).toHaveBeenCalledWith(123);
                expect(comp.questionStatistic).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
