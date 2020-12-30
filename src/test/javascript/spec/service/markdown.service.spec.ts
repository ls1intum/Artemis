import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { getTestBed } from '@angular/core/testing';

chai.use(sinonChai);
const expect = chai.expect;

describe('Markdown Service', () => {
    let markdownService: ArtemisMarkdownService;

    beforeEach(() => {
        const injector = getTestBed();
        markdownService = injector.get(ArtemisMarkdownService);
    });

    const hintText = 'Add an explanation here (only visible in feedback after quiz has ended)';
    const markdownHint = '[hint] ' + hintText;
    const explanationText = 'Add an explanation here (only visible in feedback after quiz has ended)';
    const markdownExplanation = '[exp] ' + explanationText;

    it('should return correct parsed text hint and explanation for MC questions', () => {
        let markdownElement = new MultipleChoiceQuestion();
        const markdownString = '[ ] Enter a correct answer option here';

        ArtemisMarkdownService.parseTextHintExplanation(markdownString, markdownElement);
        expect(markdownElement.text).to.equal(markdownString);
        expect(markdownElement.hint).to.be.undefined;
        expect(markdownElement.explanation).to.be.undefined;

        markdownElement = new MultipleChoiceQuestion();
        ArtemisMarkdownService.parseTextHintExplanation(`${markdownHint}`, markdownElement);
        expect(markdownElement.text).to.equal('');
        expect(markdownElement.hint).to.equal(hintText);
        expect(markdownElement.explanation).to.be.undefined;

        markdownElement = new MultipleChoiceQuestion();
        ArtemisMarkdownService.parseTextHintExplanation(`${markdownExplanation}`, markdownElement);
        expect(markdownElement.text).to.equal('');
        expect(markdownElement.hint).to.be.undefined;
        expect(markdownElement.explanation).to.equal(explanationText);

        markdownElement = new MultipleChoiceQuestion();
        ArtemisMarkdownService.parseTextHintExplanation(`${markdownString} ${markdownHint} ${markdownExplanation}`, markdownElement);
        expect(markdownElement.text).to.equal(markdownString);
        expect(markdownElement.hint).to.equal(hintText);
        expect(markdownElement.explanation).to.equal(explanationText);
    });

    it('should return correct parsed text hint and explanation for SA questions', () => {
        let markdownElement = new ShortAnswerQuestion();
        const markdownString =
            'Enter your long question if needed\n' +
            'Select a part of the text and click on Add Spot to automatically create an input field and the corresponding mapping\n' +
            'You can define a input field like this: This [-spot 1] an [-spot 2] field.\n' +
            'To define the solution for the input fields you need to create a mapping (multiple mapping also possible):';

        ArtemisMarkdownService.parseTextHintExplanation(markdownString, markdownElement);
        expect(markdownElement.text).to.equal(markdownString);
        expect(markdownElement.hint).to.be.undefined;
        expect(markdownElement.explanation).to.be.undefined;

        markdownElement = new ShortAnswerQuestion();
        ArtemisMarkdownService.parseTextHintExplanation(`${markdownHint}`, markdownElement);
        expect(markdownElement.text).to.equal('');
        expect(markdownElement.hint).to.equal(hintText);
        expect(markdownElement.explanation).to.be.undefined;

        markdownElement = new ShortAnswerQuestion();
        ArtemisMarkdownService.parseTextHintExplanation(`${markdownExplanation}`, markdownElement);
        expect(markdownElement.text).to.equal('');
        expect(markdownElement.hint).to.be.undefined;
        expect(markdownElement.explanation).to.equal(explanationText);

        markdownElement = new ShortAnswerQuestion();
        ArtemisMarkdownService.parseTextHintExplanation(`${markdownString} ${markdownHint} ${markdownExplanation}`, markdownElement);
        expect(markdownElement.text).to.equal(markdownString);
        expect(markdownElement.hint).to.equal(hintText);
        expect(markdownElement.explanation).to.equal(explanationText);
    });

    it('should return sanitized markdown for undefined input', () => {
        const emptyMarkdown = undefined;
        const safeMarkdown = markdownService.htmlForMarkdown(emptyMarkdown);
        expect(safeMarkdown).to.equal('');
    });

    it('should return sanitized markdown for html input', () => {
        const markdownString = '<b style="background-color: blue">Will this render blue?</b>';

        // Don't disable any html tags or tags
        const safeMarkdownWithTagsAndAttributes = markdownService.htmlForMarkdown(markdownString);
        expect(safeMarkdownWithTagsAndAttributes).to.equal('<p><b style="background-color: blue">Will this render blue?</b></p>');

        // Don't disable any html tags but disallow attributes
        const safeMarkdownWithTags = markdownService.htmlForMarkdown(markdownString, [], undefined, []);
        expect(safeMarkdownWithTags).to.equal('<p><b>Will this render blue?</b></p>');

        // Don't disable any html tags and allow one specific attribute
        const safeMarkdownWithTagsAndAttributeA = markdownService.htmlForMarkdown(markdownString, [], undefined, ['unused']);
        expect(safeMarkdownWithTagsAndAttributeA).to.equal('<p><b>Will this render blue?</b></p>');
        const safeMarkdownWithTagsAndAttributeB = markdownService.htmlForMarkdown(markdownString, [], undefined, ['style']);
        expect(safeMarkdownWithTagsAndAttributeB).to.equal('<p><b style="background-color: blue">Will this render blue?</b></p>');

        // Don't disable any html attributes but disallow tags (attributes of disallowed tags are gone too)
        const safeMarkdownWithAttributes = markdownService.htmlForMarkdown(markdownString, [], []);
        expect(safeMarkdownWithAttributes).to.equal('Will this render blue?');

        // Only allow one specific html tag but disallow attributes
        const safeMarkdownWithSingleTagAndNoAttributes = markdownService.htmlForMarkdown(markdownString, [], ['b'], []);
        expect(safeMarkdownWithSingleTagAndNoAttributes).to.equal('<b>Will this render blue?</b>');

        // Only allow one specific html tag and allow all attributes
        const safeMarkdownWithSingleTagAndAttributes = markdownService.htmlForMarkdown(markdownString, [], ['b']);
        expect(safeMarkdownWithSingleTagAndAttributes).to.equal('<b style="background-color: blue">Will this render blue?</b>');

        // Disable all html tags or attributes
        const safeMarkdownWithoutExtras = markdownService.htmlForMarkdown(markdownString, [], [], []);
        expect(safeMarkdownWithoutExtras).to.equal('Will this render blue?');
    });
});
