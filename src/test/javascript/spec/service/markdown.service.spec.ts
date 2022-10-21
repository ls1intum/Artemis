import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { parseExerciseHintExplanation } from 'app/shared/util/markdown.util';
import { htmlForMarkdown } from 'app/shared/util/markdown.conversion.util';

describe('Markdown Service', () => {
    const hintText = 'Add an explanation here (only visible in feedback after quiz has ended)';
    const markdownHint = '[hint] ' + hintText;
    const explanationText = 'Add an explanation here (only visible in feedback after quiz has ended)';
    const markdownExplanation = '[exp] ' + explanationText;

    it('should return correct parsed exercise hint and explanation for MC questions', () => {
        let markdownElement = new MultipleChoiceQuestion();
        const markdownString = '[ ] Enter a correct answer option here';

        parseExerciseHintExplanation(markdownString, markdownElement);
        expect(markdownElement.text).toEqual(markdownString);
        expect(markdownElement.hint).toBeUndefined();
        expect(markdownElement.explanation).toBeUndefined();

        markdownElement = new MultipleChoiceQuestion();
        parseExerciseHintExplanation(`${markdownHint}`, markdownElement);
        expect(markdownElement.text).toBe('');
        expect(markdownElement.hint).toEqual(hintText);
        expect(markdownElement.explanation).toBeUndefined();

        markdownElement = new MultipleChoiceQuestion();
        parseExerciseHintExplanation(`${markdownExplanation}`, markdownElement);
        expect(markdownElement.text).toBe('');
        expect(markdownElement.hint).toBeUndefined();
        expect(markdownElement.explanation).toEqual(explanationText);

        markdownElement = new MultipleChoiceQuestion();
        parseExerciseHintExplanation(`${markdownString} ${markdownHint} ${markdownExplanation}`, markdownElement);
        expect(markdownElement.text).toEqual(markdownString);
        expect(markdownElement.hint).toEqual(hintText);
        expect(markdownElement.explanation).toEqual(explanationText);
    });

    it('should return correct parsed exercise hint and explanation for SA questions', () => {
        let markdownElement = new ShortAnswerQuestion();
        const markdownString =
            'Enter your long question if needed\n' +
            'Select a part of the text and click on Add Spot to automatically create an input field and the corresponding mapping\n' +
            'You can define a input field like this: This [-spot 1] an [-spot 2] field.\n' +
            'To define the solution for the input fields you need to create a mapping (multiple mapping also possible):';

        parseExerciseHintExplanation(markdownString, markdownElement);
        expect(markdownElement.text).toEqual(markdownString);
        expect(markdownElement.hint).toBeUndefined();
        expect(markdownElement.explanation).toBeUndefined();

        markdownElement = new ShortAnswerQuestion();
        parseExerciseHintExplanation(`${markdownHint}`, markdownElement);
        expect(markdownElement.text).toBe('');
        expect(markdownElement.hint).toEqual(hintText);
        expect(markdownElement.explanation).toBeUndefined();

        markdownElement = new ShortAnswerQuestion();
        parseExerciseHintExplanation(`${markdownExplanation}`, markdownElement);
        expect(markdownElement.text).toBe('');
        expect(markdownElement.hint).toBeUndefined();
        expect(markdownElement.explanation).toEqual(explanationText);

        markdownElement = new ShortAnswerQuestion();
        parseExerciseHintExplanation(`${markdownString} ${markdownHint} ${markdownExplanation}`, markdownElement);
        expect(markdownElement.text).toEqual(markdownString);
        expect(markdownElement.hint).toEqual(hintText);
        expect(markdownElement.explanation).toEqual(explanationText);
    });

    it('should return sanitized markdown for undefined input', () => {
        const emptyMarkdown = undefined;
        const safeMarkdown = htmlForMarkdown(emptyMarkdown);
        expect(safeMarkdown).toBe('');
    });

    it('should return sanitized markdown for html input', () => {
        const markdownString = '<b style="background-color: blue">Will this render blue?</b>';

        // Don't disable any html tags or tags
        const safeMarkdownWithTagsAndAttributes = htmlForMarkdown(markdownString);
        expect(safeMarkdownWithTagsAndAttributes).toBe('<p><b style="background-color: blue">Will this render blue?</b></p>');

        // Don't disable any html tags but disallow attributes
        const safeMarkdownWithTags = htmlForMarkdown(markdownString, [], undefined, []);
        expect(safeMarkdownWithTags).toBe('<p><b>Will this render blue?</b></p>');

        // Don't disable any html tags and allow one specific attribute
        const safeMarkdownWithTagsAndAttributeA = htmlForMarkdown(markdownString, [], undefined, ['unused']);
        expect(safeMarkdownWithTagsAndAttributeA).toBe('<p><b>Will this render blue?</b></p>');
        const safeMarkdownWithTagsAndAttributeB = htmlForMarkdown(markdownString, [], undefined, ['style']);
        expect(safeMarkdownWithTagsAndAttributeB).toBe('<p><b style="background-color: blue">Will this render blue?</b></p>');

        // Don't disable any html attributes but disallow tags (attributes of disallowed tags are gone too)
        const safeMarkdownWithAttributes = htmlForMarkdown(markdownString, [], []);
        expect(safeMarkdownWithAttributes).toBe('Will this render blue?');

        // Only allow one specific html tag but disallow attributes
        const safeMarkdownWithSingleTagAndNoAttributes = htmlForMarkdown(markdownString, [], ['b'], []);
        expect(safeMarkdownWithSingleTagAndNoAttributes).toBe('<b>Will this render blue?</b>');

        // Only allow one specific html tag and allow all attributes
        const safeMarkdownWithSingleTagAndAttributes = htmlForMarkdown(markdownString, [], ['b']);
        expect(safeMarkdownWithSingleTagAndAttributes).toBe('<b style="background-color: blue">Will this render blue?</b>');

        // Disable all html tags or attributes
        const safeMarkdownWithoutExtras = htmlForMarkdown(markdownString, [], [], []);
        expect(safeMarkdownWithoutExtras).toBe('Will this render blue?');
    });
});
