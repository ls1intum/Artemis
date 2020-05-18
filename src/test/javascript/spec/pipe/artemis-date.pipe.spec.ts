import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as moment from 'moment';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('ArtemisDatePipe', () => {
    let pipe: ArtemisDatePipe;
    let translateService: TranslateService;
    const dateTime = moment({
        year: 2020,
        month: 3,
        day: 14,
        hour: 9,
        minute: 27,
        seconds: 3,
    }); // 14/04/2020 15:27:33

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [ArtemisDatePipe],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });
        translateService = TestBed.inject(TranslateService);
        pipe = new ArtemisDatePipe(translateService);
    });

    describe('en locale', () => {
        beforeEach(() => {
            dateTime.locale('en');
            translateService.currentLang = 'en';
        });

        describe('without seconds', () => {
            it('Should return format equal to "14/4/20 9:27" with format parameter set to "short"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short');
                expect(localizedDateTime).to.be.equal(dateTime.format('D/M/YY H:m'));
                expect(localizedDateTime).to.be.equal('14/4/20 9:27');
            });

            it('Should return format equal to "Apr 14, 2020 9:27 AM" with format parameter set to "long"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long');
                expect(localizedDateTime).to.be.equal(dateTime.format('ll LT'));
                expect(localizedDateTime).to.be.equal('Apr 14, 2020 9:27 AM');
            });

            it('Should return format equal to "14/4/20" with format parameter set to "short-date"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short-date');
                expect(localizedDateTime).to.be.equal(dateTime.format('D/M/YY'));
                expect(localizedDateTime).to.be.equal('14/4/20');
            });

            it('Should return format equal to "Apr 14, 2020" with format parameter set to "long-date"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long-date');
                expect(localizedDateTime).to.be.equal(dateTime.format('ll'));
                expect(localizedDateTime).to.be.equal('Apr 14, 2020');
            });

            it('Should return format equal to "9:27" with format parameter set to "short-time"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short-time');
                expect(localizedDateTime).to.be.equal(dateTime.format('H:m'));
                expect(localizedDateTime).to.be.equal('9:27');
            });

            it('Should return format equal to "9:27 AM" with format parameter set to "long-time"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long-time');
                expect(localizedDateTime).to.be.equal(dateTime.format('LT'));
                expect(localizedDateTime).to.be.equal('9:27 AM');
            });
        });

        describe('with seconds', () => {
            it('Should return format equal to "14/4/20 9:27:3" with format parameter set to "short"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short', true);
                expect(localizedDateTime).to.be.equal(dateTime.format('D/M/YY H:m:s'));
                expect(localizedDateTime).to.be.equal('14/4/20 9:27:3');
            });

            it('Should return format equal to "Apr 14, 2020 9:27:03 AM" with format parameter set to "long"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long', true);
                expect(localizedDateTime).to.be.equal(dateTime.format('ll LTS'));
                expect(localizedDateTime).to.be.equal('Apr 14, 2020 9:27:03 AM');
            });

            it('Should return format equal to "14/4/20" with format parameter set to "short-date"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short-date', true);
                expect(localizedDateTime).to.be.equal(dateTime.format('D/M/YY'));
                expect(localizedDateTime).to.be.equal('14/4/20');
            });

            it('Should return format equal to "Apr 14, 2020" with format parameter set to "long-date"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long-date', true);
                expect(localizedDateTime).to.be.equal(dateTime.format('ll'));
                expect(localizedDateTime).to.be.equal('Apr 14, 2020');
            });

            it('Should return format equal to "9:27:3" with format parameter set to "short-time"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short-time', true);
                expect(localizedDateTime).to.be.equal(dateTime.format('H:m:s'));
                expect(localizedDateTime).to.be.equal('9:27:3');
            });

            it('Should return format equal to "9:27:03 AM" with format parameter set to "long-time"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long-time', true);
                expect(localizedDateTime).to.be.equal(dateTime.format('LTS'));
                expect(localizedDateTime).to.be.equal('9:27:03 AM');
            });
        });
    });

    describe('de locale', () => {
        beforeEach(() => {
            dateTime.locale('de');
            translateService.currentLang = 'de';
        });

        describe('without seconds', () => {
            it('Should return format equal to "14.4.20 9:27" with format parameter set to "short"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short');
                expect(localizedDateTime).to.be.equal(dateTime.format('D.M.YY H:m'));
                expect(localizedDateTime).to.be.equal('14.4.20 9:27');
            });

            it('Should return format equal to "14. Apr. 2020 09:27" with format parameter set to "long"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long');
                expect(localizedDateTime).to.be.equal(dateTime.format('ll LT'));
                expect(localizedDateTime).to.be.equal('14. Apr. 2020 09:27');
            });

            it('Should return format equal to "14.4.20" with format parameter set to "short-date"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short-date');
                expect(localizedDateTime).to.be.equal(dateTime.format('D.M.YY'));
                expect(localizedDateTime).to.be.equal('14.4.20');
            });

            it('Should return format equal to "14. Apr. 2020" with format parameter set to "long-date"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long-date');
                expect(localizedDateTime).to.be.equal(dateTime.format('ll'));
                expect(localizedDateTime).to.be.equal('14. Apr. 2020');
            });

            it('Should return format equal to "9:27" with format parameter set to "short-time"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short-time');
                expect(localizedDateTime).to.be.equal(dateTime.format('H:m'));
                expect(localizedDateTime).to.be.equal('9:27');
            });

            it('Should return format equal to "09:27" with format parameter set to "long-time"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long-time');
                expect(localizedDateTime).to.be.equal(dateTime.format('LT'));
                expect(localizedDateTime).to.be.equal('09:27');
            });
        });

        describe('with seconds', () => {
            it('Should return format equal to "14.4.20 9:27:3" with format parameter set to "short"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short', true);
                expect(localizedDateTime).to.be.equal(dateTime.format('D.M.YY H:m:s'));
                expect(localizedDateTime).to.be.equal('14.4.20 9:27:3');
            });

            it('Should return format equal to "14. Apr. 2020 09:27:03" with format parameter set to "long"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long', true);
                expect(localizedDateTime).to.be.equal(dateTime.format('ll LTS'));
                expect(localizedDateTime).to.be.equal('14. Apr. 2020 09:27:03');
            });

            it('Should return format equal to "14.4.20" with format parameter set to "short-date"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short-date', true);
                expect(localizedDateTime).to.be.equal(dateTime.format('D.M.YY'));
                expect(localizedDateTime).to.be.equal('14.4.20');
            });

            it('Should return format equal to "14. Apr. 2020" with format parameter set to "long-date"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long-date', true);
                expect(localizedDateTime).to.be.equal(dateTime.format('ll'));
                expect(localizedDateTime).to.be.equal('14. Apr. 2020');
            });

            it('Should return format equal to "9:27:3" with format parameter set to "short-time"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short-time', true);
                expect(localizedDateTime).to.be.equal(dateTime.format('H:m:s'));
                expect(localizedDateTime).to.be.equal('9:27:3');
            });

            it('Should return format equal to "09:27:03" with format parameter set to "long-time"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long-time', true);
                expect(localizedDateTime).to.be.equal(dateTime.format('LTS'));
                expect(localizedDateTime).to.be.equal('09:27:03');
            });
        });
    });
});
