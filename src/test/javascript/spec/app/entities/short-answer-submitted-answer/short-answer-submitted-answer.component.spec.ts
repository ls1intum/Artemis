/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { ShortAnswerSubmittedAnswerComponent } from 'app/entities/short-answer-submitted-answer/short-answer-submitted-answer.component';
import { ShortAnswerSubmittedAnswerService } from 'app/entities/short-answer-submitted-answer/short-answer-submitted-answer.service';
import { ShortAnswerSubmittedAnswer } from 'app/entities/short-answer-submitted-answer/short-answer-submitted-answer.model';

describe('Component Tests', () => {
    describe('ShortAnswerSubmittedAnswer Management Component', () => {
        let comp: ShortAnswerSubmittedAnswerComponent;
        let fixture: ComponentFixture<ShortAnswerSubmittedAnswerComponent>;
        let service: ShortAnswerSubmittedAnswerService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ShortAnswerSubmittedAnswerComponent],
                providers: []
            })
                .overrideTemplate(ShortAnswerSubmittedAnswerComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(ShortAnswerSubmittedAnswerComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ShortAnswerSubmittedAnswerService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new ShortAnswerSubmittedAnswer(123)],
                        headers
                    })
                )
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.shortAnswerSubmittedAnswers[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
