/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { MultipleChoiceSubmittedAnswerComponent } from 'app/entities/multiple-choice-submitted-answer/multiple-choice-submitted-answer.component';
import { MultipleChoiceSubmittedAnswerService } from 'app/entities/multiple-choice-submitted-answer/multiple-choice-submitted-answer.service';
import { MultipleChoiceSubmittedAnswer } from 'app/shared/model/multiple-choice-submitted-answer.model';

describe('Component Tests', () => {
    describe('MultipleChoiceSubmittedAnswer Management Component', () => {
        let comp: MultipleChoiceSubmittedAnswerComponent;
        let fixture: ComponentFixture<MultipleChoiceSubmittedAnswerComponent>;
        let service: MultipleChoiceSubmittedAnswerService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [MultipleChoiceSubmittedAnswerComponent],
                providers: []
            })
                .overrideTemplate(MultipleChoiceSubmittedAnswerComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(MultipleChoiceSubmittedAnswerComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(MultipleChoiceSubmittedAnswerService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new MultipleChoiceSubmittedAnswer(123)],
                        headers
                    })
                )
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.multipleChoiceSubmittedAnswers[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
