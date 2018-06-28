/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { Observable } from 'rxjs/Observable';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { MultipleChoiceSubmittedAnswerComponent } from '../../../../../../main/webapp/app/entities/multiple-choice-submitted-answer/multiple-choice-submitted-answer.component';
import { MultipleChoiceSubmittedAnswerService } from '../../../../../../main/webapp/app/entities/multiple-choice-submitted-answer/multiple-choice-submitted-answer.service';
import { MultipleChoiceSubmittedAnswer } from '../../../../../../main/webapp/app/entities/multiple-choice-submitted-answer/multiple-choice-submitted-answer.model';

describe('Component Tests', () => {

    describe('MultipleChoiceSubmittedAnswer Management Component', () => {
        let comp: MultipleChoiceSubmittedAnswerComponent;
        let fixture: ComponentFixture<MultipleChoiceSubmittedAnswerComponent>;
        let service: MultipleChoiceSubmittedAnswerService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [MultipleChoiceSubmittedAnswerComponent],
                providers: [
                    MultipleChoiceSubmittedAnswerService
                ]
            })
            .overrideTemplate(MultipleChoiceSubmittedAnswerComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(MultipleChoiceSubmittedAnswerComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(MultipleChoiceSubmittedAnswerService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN
                const headers = new HttpHeaders().append('link', 'link;link');
                spyOn(service, 'query').and.returnValue(Observable.of(new HttpResponse({
                    body: [new MultipleChoiceSubmittedAnswer(123)],
                    headers
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.query).toHaveBeenCalled();
                expect(comp.multipleChoiceSubmittedAnswers[0]).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
