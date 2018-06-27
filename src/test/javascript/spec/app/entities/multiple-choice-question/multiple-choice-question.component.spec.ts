/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { Observable } from 'rxjs/Observable';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTEMiSTestModule } from '../../../test.module';
import { MultipleChoiceQuestionComponent } from '../../../../../../main/webapp/app/entities/multiple-choice-question/multiple-choice-question.component';
import { MultipleChoiceQuestionService } from '../../../../../../main/webapp/app/entities/multiple-choice-question/multiple-choice-question.service';
import { MultipleChoiceQuestion } from '../../../../../../main/webapp/app/entities/multiple-choice-question/multiple-choice-question.model';

describe('Component Tests', () => {

    describe('MultipleChoiceQuestion Management Component', () => {
        let comp: MultipleChoiceQuestionComponent;
        let fixture: ComponentFixture<MultipleChoiceQuestionComponent>;
        let service: MultipleChoiceQuestionService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [MultipleChoiceQuestionComponent],
                providers: [
                    MultipleChoiceQuestionService
                ]
            })
            .overrideTemplate(MultipleChoiceQuestionComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(MultipleChoiceQuestionComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(MultipleChoiceQuestionService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN
                const headers = new HttpHeaders().append('link', 'link;link');
                spyOn(service, 'query').and.returnValue(Observable.of(new HttpResponse({
                    body: [new MultipleChoiceQuestion(123)],
                    headers
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.query).toHaveBeenCalled();
                expect(comp.multipleChoiceQuestions[0]).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
