import { describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { parseMarkdownForDomainActions } from 'app/editor/markdown-editor/monaco/markdown-editor-parsing.helper';
import { GradingDescriptionAction } from 'app/editor/monaco-editor/model/actions/grading-criteria/grading-description.action';
import { GradingFeedbackAction } from 'app/editor/monaco-editor/model/actions/grading-criteria/grading-feedback.action';

describe('MarkdownEditorParsingHelper', () => {
    setupTestBed({ zoneless: true });

    it('should parse markdown without domain action identifiers', () => {
        const markdown = 'This is some text that uses no domain action identifiers.';
        const result = parseMarkdownForDomainActions(markdown, []);
        expect(result).toEqual([{ text: markdown, action: undefined }]);
    });

    it('should parse single-line text with one domain action identifier', () => {
        const action = new GradingDescriptionAction();
        const markdown = 'This is some text. [description] This is a description.';
        const result = parseMarkdownForDomainActions(markdown, [action]);
        expect(result).toEqual([
            { text: 'This is some text.', action: undefined },
            { text: 'This is a description.', action },
        ]);
    });

    it('should parse single-line text with multiple domain action identifiers', () => {
        const descriptionAction = new GradingDescriptionAction();
        const feedbackAction = new GradingFeedbackAction();
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
        const descriptionAction = new GradingDescriptionAction();
        const feedbackAction = new GradingFeedbackAction();
        const markdown = 'This is some text. [description] This is a description.\n [feedback] This is some feedback.';
        const result = parseMarkdownForDomainActions(markdown, [descriptionAction, feedbackAction]);
        expect(result).toEqual([
            { text: 'This is some text.', action: undefined },
            { text: 'This is a description.', action: descriptionAction },
            { text: 'This is some feedback.', action: feedbackAction },
        ]);
    });
});
