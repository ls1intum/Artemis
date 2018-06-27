/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

import { ArTEMiSTestModule } from '../../../test.module';
import { MultipleChoiceQuestionDetailComponent } from '../../../../../../main/webapp/app/entities/multiple-choice-question/multiple-choice-question-detail.component';
import { MultipleChoiceQuestionService } from '../../../../../../main/webapp/app/entities/multiple-choice-question/multiple-choice-question.service';
import { MultipleChoiceQuestion } from '../../../../../../main/webapp/app/entities/multiple-choice-question/multiple-choice-question.model';

describe('Component Tests', () => {

    describe('MultipleChoiceQuestion Management Detail Component', () => {
        let comp: MultipleChoiceQuestionDetailComponent;
        let fixture: ComponentFixture<MultipleChoiceQuestionDetailComponent>;
        let service: MultipleChoiceQuestionService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [MultipleChoiceQuestionDetailComponent],
                providers: [
                    MultipleChoiceQuestionService
                ]
            })
            .overrideTemplate(MultipleChoiceQuestionDetailComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(MultipleChoiceQuestionDetailComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(MultipleChoiceQuestionService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                spyOn(service, 'find').and.returnValue(Observable.of(new HttpResponse({
                    body: new MultipleChoiceQuestion(123)
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.find).toHaveBeenCalledWith(123);
                expect(comp.multipleChoiceQuestion).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
