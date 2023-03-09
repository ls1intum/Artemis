import { TestBed, fakeAsync } from '@angular/core/testing';
import { ShortAnswerQuestionUtil } from 'app/exercises/quiz/shared/short-answer-question-util.service';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';

describe('ShortAnswerQuestionUtil', () => {
    let shortAnswerQuestionUtil: ShortAnswerQuestionUtil;

    beforeEach(fakeAsync(() => {
        TestBed.configureTestingModule({ providers: [ArtemisMarkdownService] });
        shortAnswerQuestionUtil = new ShortAnswerQuestionUtil();
    }));

    it('should transform text parts to html correctly', fakeAsync(() => {
        const originalTextParts1 = [['random text'], ['    some more text', '[-spot 1]'], ['last paragraph']];
        const formattedTextParts1 = [['<p>random text</p>'], ['<p>&nbsp;&nbsp;&nbsp;&nbsp;some more text</p>', '<p>[-spot 1]</p>'], ['<p>last paragraph</p>']];
        expect(shortAnswerQuestionUtil.transformTextPartsIntoHTML(originalTextParts1)).toEqual(formattedTextParts1);
        const originalTextParts2 = [['`random code`'], ['`    some more code`', '[-spot 1]'], ['`last code paragraph`']];
        const formattedTextParts2 = [
            ['<p><code>random code</code></p>'],
            ['<p><code>&nbsp;&nbsp;&nbsp;&nbsp;some more code</code></p>', '<p>[-spot 1]</p>'],
            ['<p><code>last code paragraph</code></p>'],
        ];
        expect(shortAnswerQuestionUtil.transformTextPartsIntoHTML(originalTextParts2)).toEqual(formattedTextParts2);
        const originalTextParts3 = [['`random code`'], ['    [-spot 1]', '`some more code`', '[-spot 1]'], ['`last code paragraph`']];
        const formattedTextParts3 = [
            ['<p><code>random code</code></p>'],
            ['<p>&nbsp;&nbsp;&nbsp;&nbsp;[-spot 1]</p>', '<p><code>some more code</code></p>', '<p>[-spot 1]</p>'],
            ['<p><code>last code paragraph</code></p>'],
        ];
        expect(shortAnswerQuestionUtil.transformTextPartsIntoHTML(originalTextParts3)).toEqual(formattedTextParts3);
    }));

    it('should return the correct indentation', fakeAsync(() => {
        const sentence1 = '    this is a test';
        const sentence2 = '  `another test`';
        const sentence3 = '`last test`';
        expect(shortAnswerQuestionUtil.getIndentation(sentence1)).toBe('    ');
        expect(shortAnswerQuestionUtil.getIndentation(sentence2)).toBe('  ');
        expect(shortAnswerQuestionUtil.getIndentation(sentence3)).toBe('');
    }));

    it('should return first word of a sentence', fakeAsync(() => {
        const sentence1 = '         this is a test';
        const sentence2 = '    `another test`';
        const sentence3 = '';
        expect(shortAnswerQuestionUtil.getFirstWord(sentence1)).toBe('this');
        expect(shortAnswerQuestionUtil.getFirstWord(sentence2)).toBe('another');
        expect(shortAnswerQuestionUtil.getFirstWord(sentence3)).toBe('');
    }));
});
