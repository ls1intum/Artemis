import { ShortAnswerQuestionUtil } from 'app/exercises/quiz/shared/short-answer-question-util.service';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { fakeAsync, TestBed } from '@angular/core/testing';
import * as chai from 'chai';
import sinonChai from 'sinon-chai';

chai.use(sinonChai);
const expect = chai.expect;

describe('ShortAnswerQuestionUtil', () => {
    let shortAnswerQuestionUtil: ShortAnswerQuestionUtil;
    let artemisMarkdownService: ArtemisMarkdownService;

    beforeEach(fakeAsync(() => {
        TestBed.configureTestingModule({ providers: [ArtemisMarkdownService] });
        artemisMarkdownService = TestBed.inject(ArtemisMarkdownService);
        shortAnswerQuestionUtil = new ShortAnswerQuestionUtil();
    }));

    it('Should transform text parts to html correctly', fakeAsync(() => {
        const originalTextParts1 = [['random text'], ['    some more text', '[-spot 1]'], ['last paragraph']];
        const formattedTextParts1 = [['<p>random text</p>'], ['<p>&nbsp;&nbsp;&nbsp;&nbsp;some more text</p>', '<p>[-spot 1]</p>'], ['<p>last paragraph</p>']];
        expect(shortAnswerQuestionUtil.transformTextPartsIntoHTML(originalTextParts1, artemisMarkdownService)).to.eql(formattedTextParts1);
        const originalTextParts2 = [['`random code`'], ['`    some more code`', '[-spot 1]'], ['`last code paragraph`']];
        const formattedTextParts2 = [
            ['<p><code>random code</code></p>'],
            ['<p><code>&nbsp;&nbsp;&nbsp;&nbsp;some more code</code></p>', '<p>[-spot 1]</p>'],
            ['<p><code>last code paragraph</code></p>'],
        ];
        expect(shortAnswerQuestionUtil.transformTextPartsIntoHTML(originalTextParts2, artemisMarkdownService)).to.eql(formattedTextParts2);
        const originalTextParts3 = [['`random code`'], ['    [-spot 1]', '`some more code`', '[-spot 1]'], ['`last code paragraph`']];
        const formattedTextParts3 = [
            ['<p><code>random code</code></p>'],
            ['<p>&nbsp;&nbsp;&nbsp;&nbsp;[-spot 1]</p>', '<p><code>some more code</code></p>', '<p>[-spot 1]</p>'],
            ['<p><code>last code paragraph</code></p>'],
        ];
        expect(shortAnswerQuestionUtil.transformTextPartsIntoHTML(originalTextParts3, artemisMarkdownService)).to.eql(formattedTextParts3);
    }));

    it('Should return the correct indentation', fakeAsync(() => {
        const sentence1 = '    this is a test';
        const sentence2 = '  `another test`';
        const sentence3 = '`last test`';
        expect(shortAnswerQuestionUtil.getIndentation(sentence1)).to.equal('    ');
        expect(shortAnswerQuestionUtil.getIndentation(sentence2)).to.equal('  ');
        expect(shortAnswerQuestionUtil.getIndentation(sentence3)).to.equal('');
    }));

    it('Should return first word of a sentence', fakeAsync(() => {
        const sentence1 = '         this is a test';
        const sentence2 = '    `another test`';
        const sentence3 = '';
        expect(shortAnswerQuestionUtil.getFirstWord(sentence1)).to.equal('this');
        expect(shortAnswerQuestionUtil.getFirstWord(sentence2)).to.equal('another');
        expect(shortAnswerQuestionUtil.getFirstWord(sentence3)).to.equal('');
    }));
});
