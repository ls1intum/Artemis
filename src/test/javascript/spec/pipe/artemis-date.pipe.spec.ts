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
    }); // 14/04/2020 9:27:33

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
                const format = 'D/M/YY H:m';
                expect(ArtemisDatePipe.format('en', 'short')).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('14/4/20 9:27');
            });

            it('Should return format equal to "Apr 14, 2020 9:27 AM" with format parameter set to "long"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long');
                const format = 'll LT';
                expect(ArtemisDatePipe.format('en', 'long')).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('Apr 14, 2020 9:27 AM');
            });

            it('Should return format equal to "14/4/20" with format parameter set to "short-date"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short-date');
                const format = 'D/M/YY';
                expect(ArtemisDatePipe.format('en', 'short-date')).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('14/4/20');
            });

            it('Should return format equal to "Apr 14, 2020" with format parameter set to "long-date"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long-date');
                const format = 'll';
                expect(ArtemisDatePipe.format('en', 'long-date')).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('Apr 14, 2020');
            });

            it('Should return format equal to "9:27" with format parameter set to "short-time"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short-time');
                const format = 'H:m';
                expect(ArtemisDatePipe.format('en', 'short-time')).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('9:27');
            });

            it('Should return format equal to "9:27 AM" with format parameter set to "long-time"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long-time');
                const format = 'LT';
                expect(ArtemisDatePipe.format('en', 'long-time')).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('9:27 AM');
            });
        });

        describe('with seconds', () => {
            it('Should return format equal to "14/4/20 9:27:3" with format parameter set to "short"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short', true);
                const format = 'D/M/YY H:m:s';
                expect(ArtemisDatePipe.format('en', 'short', true)).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('14/4/20 9:27:3');
            });

            it('Should return format equal to "Apr 14, 2020 9:27:03 AM" with format parameter set to "long"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long', true);
                const format = 'll LTS';
                expect(ArtemisDatePipe.format('en', 'long', true)).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('Apr 14, 2020 9:27:03 AM');
            });

            it('Should return format equal to "14/4/20" with format parameter set to "short-date"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short-date', true);
                const format = 'D/M/YY';
                expect(ArtemisDatePipe.format('en', 'short-date', true)).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('14/4/20');
            });

            it('Should return format equal to "Apr 14, 2020" with format parameter set to "long-date"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long-date', true);
                const format = 'll';
                expect(ArtemisDatePipe.format('en', 'long-date', true)).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('Apr 14, 2020');
            });

            it('Should return format equal to "9:27:3" with format parameter set to "short-time"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short-time', true);
                const format = 'H:m:s';
                expect(ArtemisDatePipe.format('en', 'short-time', true)).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('9:27:3');
            });

            it('Should return format equal to "9:27:03 AM" with format parameter set to "long-time"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long-time', true);
                const format = 'LTS';
                expect(ArtemisDatePipe.format('en', 'long-time', true)).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
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
                const format = 'D.M.YY H:m';
                expect(ArtemisDatePipe.format('de', 'short')).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('14.4.20 9:27');
            });

            it('Should return format equal to "14. Apr. 2020 09:27" with format parameter set to "long"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long');
                const format = 'll LT';
                expect(ArtemisDatePipe.format('de', 'long')).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('14. Apr. 2020 09:27');
            });

            it('Should return format equal to "14.4.20" with format parameter set to "short-date"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short-date');
                const format = 'D.M.YY';
                expect(ArtemisDatePipe.format('de', 'short-date')).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('14.4.20');
            });

            it('Should return format equal to "14. Apr. 2020" with format parameter set to "long-date"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long-date');
                const format = 'll';
                expect(ArtemisDatePipe.format('de', 'long-date')).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('14. Apr. 2020');
            });

            it('Should return format equal to "9:27" with format parameter set to "short-time"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short-time');
                const format = 'H:m';
                expect(ArtemisDatePipe.format('de', 'short-time')).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('9:27');
            });

            it('Should return format equal to "09:27" with format parameter set to "long-time"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long-time');
                const format = 'LT';
                expect(ArtemisDatePipe.format('de', 'long-time')).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('09:27');
            });
        });

        describe('with seconds', () => {
            it('Should return format equal to "14.4.20 9:27:3" with format parameter set to "short"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short', true);
                const format = 'D.M.YY H:m:s';
                expect(ArtemisDatePipe.format('de', 'short', true)).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('14.4.20 9:27:3');
            });

            it('Should return format equal to "14. Apr. 2020 09:27:03" with format parameter set to "long"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long', true);
                const format = 'll LTS';
                expect(ArtemisDatePipe.format('de', 'long', true)).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('14. Apr. 2020 09:27:03');
            });

            it('Should return format equal to "14.4.20" with format parameter set to "short-date"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short-date', true);
                const format = 'D.M.YY';
                expect(ArtemisDatePipe.format('de', 'short-date', true)).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('14.4.20');
            });

            it('Should return format equal to "14. Apr. 2020" with format parameter set to "long-date"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long-date', true);
                const format = 'll';
                expect(ArtemisDatePipe.format('de', 'long-date', true)).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('14. Apr. 2020');
            });

            it('Should return format equal to "9:27:3" with format parameter set to "short-time"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short-time', true);
                const format = 'H:m:s';
                expect(ArtemisDatePipe.format('de', 'short-time', true)).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('9:27:3');
            });

            it('Should return format equal to "09:27:03" with format parameter set to "long-time"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long-time', true);
                const format = 'LTS';
                expect(ArtemisDatePipe.format('de', 'long-time', true)).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('09:27:03');
            });
        });
    });
});
