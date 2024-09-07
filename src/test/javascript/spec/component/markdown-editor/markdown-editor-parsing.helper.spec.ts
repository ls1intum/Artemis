import { parseMarkdownForDomainActions } from 'app/shared/markdown-editor/monaco/markdown-editor-parsing.helper';
import { MonacoGradingDescriptionAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/monaco-grading-description.action';
import { MonacoGradingFeedbackAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/monaco-grading-feedback.action';

describe('MarkdownEditorParsingHelper', () => {
    it('should parse markdown without domain action identifiers', () => {
        const markdown = 'This is some text that uses no domain action identifiers.';
        const result = parseMarkdownForDomainActions(markdown, []);
        expect(result).toEqual([{ text: markdown, action: undefined }]);
    });

    it('should parse single-line text with one domain action identifier', () => {
        const action = new MonacoGradingDescriptionAction();
        const markdown = 'This is some text. [description] This is a description.';
        const result = parseMarkdownForDomainActions(markdown, [action]);
        expect(result).toEqual([
            { text: 'This is some text.', action: undefined },
            { text: 'This is a description.', action },
        ]);
    });

    it('should parse single-line text with multiple domain action identifiers', () => {
        const descriptionAction = new MonacoGradingDescriptionAction();
        const feedbackAction = new MonacoGradingFeedbackAction();
        const markdown = 'This is some text. [description] This is a description. [feedback] This is some feedback.';
        const result = parseMarkdownForDomainActions(markdown, [descriptionAction, feedbackAction]);
        expect(result).toEqual([
            { text: 'This is some text.', action: undefined },
            { text: 'This is a description.', action: descriptionAction },
            { text: 'This is some feedback.', action: feedbackAction },
        ]);
    });

    it('should parse multi-line text without domain action identifiers', () => {
        const markdown = 'This is some text that uses no domain action identifiers.\nThis is a second line.';
        const result = parseMarkdownForDomainActions(markdown, []);
        expect(result).toEqual([{ text: markdown, action: undefined }]);
    });

    it('should parse multi-line text with multiple domain action identifiers', () => {
        const descriptionAction = new MonacoGradingDescriptionAction();
        const feedbackAction = new MonacoGradingFeedbackAction();
        const markdown = 'This is some text. [description] This is a description.\n [feedback] This is some feedback.';
        const result = parseMarkdownForDomainActions(markdown, [descriptionAction, feedbackAction]);
        expect(result).toEqual([
            { text: 'This is some text.', action: undefined },
            { text: 'This is a description.', action: descriptionAction },
            { text: 'This is some feedback.', action: feedbackAction },
        ]);
    });
});
