/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { MultipleChoiceQuestionDetailComponent } from 'app/entities/multiple-choice-question/multiple-choice-question-detail.component';
import { MultipleChoiceQuestion } from 'app/shared/model/multiple-choice-question.model';

describe('Component Tests', () => {
    describe('MultipleChoiceQuestion Management Detail Component', () => {
        let comp: MultipleChoiceQuestionDetailComponent;
        let fixture: ComponentFixture<MultipleChoiceQuestionDetailComponent>;
        const route = ({ data: of({ multipleChoiceQuestion: new MultipleChoiceQuestion(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [MultipleChoiceQuestionDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(MultipleChoiceQuestionDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(MultipleChoiceQuestionDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.multipleChoiceQuestion).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
