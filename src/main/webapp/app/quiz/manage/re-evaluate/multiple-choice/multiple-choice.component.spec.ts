import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';

import { MultipleChoiceComponent } from './multiple-choice.component';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('MultipleChoice', () => {
    let component: MultipleChoiceComponent;
    let fixture: ComponentFixture<MultipleChoiceComponent>;
    const question: MultipleChoiceQuestion = new MultipleChoiceQuestion();

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MultipleChoiceComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        question.answerOptions = [];

        fixture = TestBed.createComponent(MultipleChoiceComponent);
        fixture.componentRef.setInput('question', question);
        fixture.componentRef.setInput('questionIndex', 0);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
