/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTEMiSTestModule } from '../../../test.module';
import { MultipleChoiceQuestionComponent } from 'app/entities/multiple-choice-question/multiple-choice-question.component';
import { MultipleChoiceQuestionService } from 'app/entities/multiple-choice-question/multiple-choice-question.service';
import { MultipleChoiceQuestion } from 'app/shared/model/multiple-choice-question.model';

describe('Component Tests', () => {
    describe('MultipleChoiceQuestion Management Component', () => {
        let comp: MultipleChoiceQuestionComponent;
        let fixture: ComponentFixture<MultipleChoiceQuestionComponent>;
        let service: MultipleChoiceQuestionService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [MultipleChoiceQuestionComponent],
                providers: []
            })
                .overrideTemplate(MultipleChoiceQuestionComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(MultipleChoiceQuestionComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(MultipleChoiceQuestionService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new MultipleChoiceQuestion(123)],
                        headers
                    })
                )
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.multipleChoiceQuestions[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
