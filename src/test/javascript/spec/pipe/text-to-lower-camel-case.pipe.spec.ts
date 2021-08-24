import { TestBed } from '@angular/core/testing';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { TextToLowerCamelCasePipe } from 'app/shared/pipes/text-to-lower-camel-case.pipe';

chai.use(sinonChai);
const expect = chai.expect;

describe('TextToLowerCamelCasePipe', () => {
    let pipe: TextToLowerCamelCasePipe;
    let pipeOutput: string;

    beforeAll(() => {
        TestBed.configureTestingModule({
            declarations: [TextToLowerCamelCasePipe],
        });
        pipe = new TextToLowerCamelCasePipe();
    });

    it('return empty string if given empty string', () => {
        pipeOutput = pipe.transform('');
        expect(pipeOutput).to.be.equal('');
    });

    it('check extra white space', () => {
        pipeOutput = pipe.transform('  EXTRA  SPACE     ');
        expect(pipeOutput).to.be.equal('extraSpace');
    });

    describe('Check single concatenated alphanumeric combination', () => {
        it('single lowercase word', () => {
            pipeOutput = pipe.transform('word');
            expect(pipeOutput).to.be.equal('word');
        });

        it('single uppercase word', () => {
            pipeOutput = pipe.transform('WORD');
            expect(pipeOutput).to.be.equal('word');
        });

        it('single normal word', () => {
            pipeOutput = pipe.transform('Word');
            expect(pipeOutput).to.be.equal('word');
        });

        it('single mixed word', () => {
            pipeOutput = pipe.transform('WoRD');
            expect(pipeOutput).to.be.equal('word');
        });

        it('single alphanumeric combination', () => {
            pipeOutput = pipe.transform('A1bC23ö4');
            expect(pipeOutput).to.be.equal('a1bc23ö4');
        });
    });

    it('single alphanumeric combination', () => {
        pipeOutput = pipe.transform('A1bC23ö4 Hello W0RLD');
        expect(pipeOutput).to.be.equal('a1bc23ö4HelloW0rld');
    });
});
