import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { SinonStub } from 'sinon';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('ArtemisDurationFromSecondsPipe', () => {
    let pipe: ArtemisDurationFromSecondsPipe;
    let translateService: TranslateService;
    let translateStub: SinonStub;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [ArtemisDurationFromSecondsPipe],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });
        translateService = TestBed.inject(TranslateService);
        pipe = new ArtemisDurationFromSecondsPipe(translateService);
    });

    it('Should return correct format for only seconds with one digit', () => {
        const localizedDuration = pipe.transform(7);
        expect(localizedDuration).to.be.equal('00:07');
    });

    it('Should return correct format for only seconds with two digits', () => {
        const localizedDuration = pipe.transform(42);
        expect(localizedDuration).to.be.equal('00:42');
    });

    it('Should return correct format for minutes with one digit', () => {
        const localizedDuration = pipe.transform(135);
        expect(localizedDuration).to.be.equal('02:15');
    });

    it('Should return correct format for minutes with two digits', () => {
        const localizedDuration = pipe.transform(1326);
        expect(localizedDuration).to.be.equal('22:06');
    });

    it('Should return correct format for hours with one digit', () => {
        const localizedDuration = pipe.transform(40645);
        expect(localizedDuration).to.be.equal('11:17:25');
    });

    it('Should return correct format for hours with two digits', () => {
        const localizedDuration = pipe.transform(21922);
        expect(localizedDuration).to.be.equal('06:05:22');
    });

    describe('en locale', () => {
        it('Should return correct format for one day', () => {
            translateStub = sinon.stub(translateService, 'instant');
            translateStub.returns('day');
            const localizedDuration = pipe.transform(108322);
            expect(localizedDuration).to.be.equal('1 day 06:05:22');
        });

        it('Should return correct format for days', () => {
            translateStub = sinon.stub(translateService, 'instant');
            translateStub.returns('days');
            const localizedDuration = pipe.transform(1357800);
            expect(localizedDuration).to.be.equal('15 days 17:10:00');
        });
    });

    describe('de locale', () => {
        it('Should return correct format for one day', () => {
            translateStub = sinon.stub(translateService, 'instant');
            translateStub.returns('Tag');
            const localizedDuration = pipe.transform(108322);
            expect(localizedDuration).to.be.equal('1 Tag 06:05:22');
        });

        it('Should return correct format for days', () => {
            translateStub = sinon.stub(translateService, 'instant');
            translateStub.returns('Tage');
            const localizedDuration = pipe.transform(1357800);
            expect(localizedDuration).to.be.equal('15 Tage 17:10:00');
        });
    });
});
