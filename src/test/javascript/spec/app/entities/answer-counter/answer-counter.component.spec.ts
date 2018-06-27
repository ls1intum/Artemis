/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { Observable } from 'rxjs/Observable';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTEMiSTestModule } from '../../../test.module';
import { AnswerCounterComponent } from '../../../../../../main/webapp/app/entities/answer-counter/answer-counter.component';
import { AnswerCounterService } from '../../../../../../main/webapp/app/entities/answer-counter/answer-counter.service';
import { AnswerCounter } from '../../../../../../main/webapp/app/entities/answer-counter/answer-counter.model';

describe('Component Tests', () => {

    describe('AnswerCounter Management Component', () => {
        let comp: AnswerCounterComponent;
        let fixture: ComponentFixture<AnswerCounterComponent>;
        let service: AnswerCounterService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [AnswerCounterComponent],
                providers: [
                    AnswerCounterService
                ]
            })
            .overrideTemplate(AnswerCounterComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(AnswerCounterComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(AnswerCounterService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN
                const headers = new HttpHeaders().append('link', 'link;link');
                spyOn(service, 'query').and.returnValue(Observable.of(new HttpResponse({
                    body: [new AnswerCounter(123)],
                    headers
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.query).toHaveBeenCalled();
                expect(comp.answerCounters[0]).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
