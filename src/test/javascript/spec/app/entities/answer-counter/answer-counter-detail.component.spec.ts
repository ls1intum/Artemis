/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

import { ArTEMiSTestModule } from '../../../test.module';
import { AnswerCounterDetailComponent } from '../../../../../../main/webapp/app/entities/answer-counter/answer-counter-detail.component';
import { AnswerCounterService } from '../../../../../../main/webapp/app/entities/answer-counter/answer-counter.service';
import { AnswerCounter } from '../../../../../../main/webapp/app/entities/answer-counter/answer-counter.model';

describe('Component Tests', () => {

    describe('AnswerCounter Management Detail Component', () => {
        let comp: AnswerCounterDetailComponent;
        let fixture: ComponentFixture<AnswerCounterDetailComponent>;
        let service: AnswerCounterService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [AnswerCounterDetailComponent],
                providers: [
                    AnswerCounterService
                ]
            })
            .overrideTemplate(AnswerCounterDetailComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(AnswerCounterDetailComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(AnswerCounterService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                spyOn(service, 'find').and.returnValue(Observable.of(new HttpResponse({
                    body: new AnswerCounter(123)
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.find).toHaveBeenCalledWith(123);
                expect(comp.answerCounter).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
