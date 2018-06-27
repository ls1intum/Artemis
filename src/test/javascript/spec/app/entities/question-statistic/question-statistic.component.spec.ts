/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { Observable } from 'rxjs/Observable';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTEMiSTestModule } from '../../../test.module';
import { QuestionStatisticComponent } from '../../../../../../main/webapp/app/entities/question-statistic/question-statistic.component';
import { QuestionStatisticService } from '../../../../../../main/webapp/app/entities/question-statistic/question-statistic.service';
import { QuestionStatistic } from '../../../../../../main/webapp/app/entities/question-statistic/question-statistic.model';

describe('Component Tests', () => {

    describe('QuestionStatistic Management Component', () => {
        let comp: QuestionStatisticComponent;
        let fixture: ComponentFixture<QuestionStatisticComponent>;
        let service: QuestionStatisticService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [QuestionStatisticComponent],
                providers: [
                    QuestionStatisticService
                ]
            })
            .overrideTemplate(QuestionStatisticComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(QuestionStatisticComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(QuestionStatisticService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN
                const headers = new HttpHeaders().append('link', 'link;link');
                spyOn(service, 'query').and.returnValue(Observable.of(new HttpResponse({
                    body: [new QuestionStatistic(123)],
                    headers
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.query).toHaveBeenCalled();
                expect(comp.questionStatistics[0]).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
