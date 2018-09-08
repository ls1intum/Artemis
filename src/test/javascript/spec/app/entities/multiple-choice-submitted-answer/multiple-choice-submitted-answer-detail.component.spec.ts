/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { MultipleChoiceSubmittedAnswerDetailComponent } from 'app/entities/multiple-choice-submitted-answer/multiple-choice-submitted-answer-detail.component';
import { MultipleChoiceSubmittedAnswer } from 'app/shared/model/multiple-choice-submitted-answer.model';

describe('Component Tests', () => {
    describe('MultipleChoiceSubmittedAnswer Management Detail Component', () => {
        let comp: MultipleChoiceSubmittedAnswerDetailComponent;
        let fixture: ComponentFixture<MultipleChoiceSubmittedAnswerDetailComponent>;
        const route = ({ data: of({ multipleChoiceSubmittedAnswer: new MultipleChoiceSubmittedAnswer(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [MultipleChoiceSubmittedAnswerDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(MultipleChoiceSubmittedAnswerDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(MultipleChoiceSubmittedAnswerDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.multipleChoiceSubmittedAnswer).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
