import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ShortAnswerQuestionComponent } from 'app/exercises/quiz/shared/questions/short-answer-question/short-answer-question.component';
import { ShortAnswerSpot } from 'app/entities/quiz/short-answer-spot.model';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { ScoringType } from 'app/entities/quiz/quiz-question.model';
import { ShortAnswerSolution } from 'app/entities/quiz/short-answer-solution.model';
import { ShortAnswerMapping } from 'app/entities/quiz/short-answer-mapping.model';
import { ShortAnswerSubmittedText } from 'app/entities/quiz/short-answer-submitted-text.model';

describe('ShortAnswerQuestion Component', () => {
    let comp: ShortAnswerQuestionComponent;
    let fixture: ComponentFixture<ShortAnswerQuestionComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [ShortAnswerQuestionComponent],
        })
            .overrideTemplate(ShortAnswerQuestionComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(ShortAnswerQuestionComponent);
        comp = fixture.componentInstance;

        const shortAnswerQuestion = new ShortAnswerQuestion();
        shortAnswerQuestion.title = '';
        shortAnswerQuestion.text = '[-spot 1] and [-spot 2] and [-spot 3]\n[-option 1] a\n[-option 2] b\n[-option 3] c';
        shortAnswerQuestion.scoringType = ScoringType.ALL_OR_NOTHING;
        shortAnswerQuestion.randomizeOrder = true;
        shortAnswerQuestion.points = 1;
        const shortAnswerSpot1 = new ShortAnswerSpot();
        shortAnswerSpot1.spotNr = 1;
        const shortAnswerSpot2 = new ShortAnswerSpot();
        shortAnswerSpot2.spotNr = 2;
        const shortAnswerSpot3 = new ShortAnswerSpot();
        shortAnswerSpot3.spotNr = 3;
        shortAnswerQuestion.spots = [shortAnswerSpot1, shortAnswerSpot2, shortAnswerSpot3];
        const shortAnswerSolution1 = new ShortAnswerSolution();
        shortAnswerSolution1.text = 'a';
        const shortAnswerSolution2 = new ShortAnswerSolution();
        shortAnswerSolution2.text = 'b';
        const shortAnswerSolution3 = new ShortAnswerSolution();
        shortAnswerSolution3.text = 'c';
        shortAnswerQuestion.solutions = [shortAnswerSolution1, shortAnswerSolution2, shortAnswerSolution3];
        const shortAnswerMapping1 = new ShortAnswerMapping(shortAnswerSpot1, shortAnswerSolution1);
        const shortAnswerMapping2 = new ShortAnswerMapping(shortAnswerSpot2, shortAnswerSolution2);
        const shortAnswerMapping3 = new ShortAnswerMapping(shortAnswerSpot3, shortAnswerSolution3);
        shortAnswerQuestion.correctMappings = [shortAnswerMapping1, shortAnswerMapping2, shortAnswerMapping3];
        comp.question = shortAnswerQuestion;

        const submittedText1 = new ShortAnswerSubmittedText();
        submittedText1.spot = shortAnswerSpot1;
        submittedText1.isCorrect = true;
        submittedText1.text = ' a ';
        const submittedText2 = new ShortAnswerSubmittedText();
        submittedText2.spot = shortAnswerSpot2;
        submittedText2.isCorrect = true;
        submittedText2.text = 'b\n';
        const submittedText3 = new ShortAnswerSubmittedText();
        submittedText3.spot = shortAnswerSpot3;
        submittedText3.isCorrect = false;
        submittedText3.text = 'not c\n';
        comp.submittedTexts = [submittedText1, submittedText2, submittedText3];
    });

    it('Should trim submitted answers', () => {
        expect(comp.isSubmittedTextCompletelyCorrect('[-spot 1]')).toEqual(true);
        expect(comp.isSubmittedTextCompletelyCorrect('[-spot 2]')).toEqual(true);
        expect(comp.isSubmittedTextCompletelyCorrect('[-spot 3]')).toEqual(false);
    });

    it('Display correct background colors', () => {
        expect(comp.getBackgroundColourForInputField('[-spot 1]')).toEqual('lightgreen');
        expect(comp.getBackgroundColourForInputField('[-spot 2]')).toEqual('lightgreen');
        expect(comp.getBackgroundColourForInputField('[-spot 3]')).toEqual('red');
    });
});
