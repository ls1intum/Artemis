/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

import { ArTEMiSTestModule } from '../../../test.module';
import { QuizPointStatisticDetailComponent } from '../../../../../../main/webapp/app/entities/quiz-point-statistic/quiz-point-statistic-detail.component';
import { QuizPointStatisticService } from '../../../../../../main/webapp/app/entities/quiz-point-statistic/quiz-point-statistic.service';
import { QuizPointStatistic } from '../../../../../../main/webapp/app/entities/quiz-point-statistic/quiz-point-statistic.model';

describe('Component Tests', () => {

    describe('QuizPointStatistic Management Detail Component', () => {
        let comp: QuizPointStatisticDetailComponent;
        let fixture: ComponentFixture<QuizPointStatisticDetailComponent>;
        let service: QuizPointStatisticService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [QuizPointStatisticDetailComponent],
                providers: [
                    QuizPointStatisticService
                ]
            })
            .overrideTemplate(QuizPointStatisticDetailComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(QuizPointStatisticDetailComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(QuizPointStatisticService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                spyOn(service, 'find').and.returnValue(Observable.of(new HttpResponse({
                    body: new QuizPointStatistic(123)
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.find).toHaveBeenCalledWith(123);
                expect(comp.quizPointStatistic).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
