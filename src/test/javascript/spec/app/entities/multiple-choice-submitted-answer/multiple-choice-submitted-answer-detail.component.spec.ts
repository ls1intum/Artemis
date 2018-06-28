/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

import { ArTEMiSTestModule } from '../../../test.module';
import { MultipleChoiceSubmittedAnswerDetailComponent } from '../../../../../../main/webapp/app/entities/multiple-choice-submitted-answer/multiple-choice-submitted-answer-detail.component';
import { MultipleChoiceSubmittedAnswerService } from '../../../../../../main/webapp/app/entities/multiple-choice-submitted-answer/multiple-choice-submitted-answer.service';
import { MultipleChoiceSubmittedAnswer } from '../../../../../../main/webapp/app/entities/multiple-choice-submitted-answer/multiple-choice-submitted-answer.model';

describe('Component Tests', () => {

    describe('MultipleChoiceSubmittedAnswer Management Detail Component', () => {
        let comp: MultipleChoiceSubmittedAnswerDetailComponent;
        let fixture: ComponentFixture<MultipleChoiceSubmittedAnswerDetailComponent>;
        let service: MultipleChoiceSubmittedAnswerService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [MultipleChoiceSubmittedAnswerDetailComponent],
                providers: [
                    MultipleChoiceSubmittedAnswerService
                ]
            })
            .overrideTemplate(MultipleChoiceSubmittedAnswerDetailComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(MultipleChoiceSubmittedAnswerDetailComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(MultipleChoiceSubmittedAnswerService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                spyOn(service, 'find').and.returnValue(Observable.of(new HttpResponse({
                    body: new MultipleChoiceSubmittedAnswer(123)
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.find).toHaveBeenCalledWith(123);
                expect(comp.multipleChoiceSubmittedAnswer).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
