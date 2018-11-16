/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { AnswerCounterComponent } from 'app/entities/answer-counter/answer-counter.component';
import { AnswerCounterService } from 'app/entities/answer-counter/answer-counter.service';
import { AnswerCounter } from 'app/shared/model/answer-counter.model';

describe('Component Tests', () => {
    describe('AnswerCounter Management Component', () => {
        let comp: AnswerCounterComponent;
        let fixture: ComponentFixture<AnswerCounterComponent>;
        let service: AnswerCounterService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [AnswerCounterComponent],
                providers: []
            })
                .overrideTemplate(AnswerCounterComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(AnswerCounterComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(AnswerCounterService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new AnswerCounter(123)],
                        headers
                    })
                )
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.answerCounters[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
