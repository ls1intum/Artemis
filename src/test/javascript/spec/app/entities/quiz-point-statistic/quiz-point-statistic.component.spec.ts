/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { Observable } from 'rxjs/Observable';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTEMiSTestModule } from '../../../test.module';
import { QuizPointStatisticComponent } from '../../../../../../main/webapp/app/entities/quiz-point-statistic/quiz-point-statistic.component';
import { QuizPointStatisticService } from '../../../../../../main/webapp/app/entities/quiz-point-statistic/quiz-point-statistic.service';
import { QuizPointStatistic } from '../../../../../../main/webapp/app/entities/quiz-point-statistic/quiz-point-statistic.model';

describe('Component Tests', () => {

    describe('QuizPointStatistic Management Component', () => {
        let comp: QuizPointStatisticComponent;
        let fixture: ComponentFixture<QuizPointStatisticComponent>;
        let service: QuizPointStatisticService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [QuizPointStatisticComponent],
                providers: [
                    QuizPointStatisticService
                ]
            })
            .overrideTemplate(QuizPointStatisticComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(QuizPointStatisticComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(QuizPointStatisticService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN
                const headers = new HttpHeaders().append('link', 'link;link');
                spyOn(service, 'query').and.returnValue(Observable.of(new HttpResponse({
                    body: [new QuizPointStatistic(123)],
                    headers
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.query).toHaveBeenCalled();
                expect(comp.quizPointStatistics[0]).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
