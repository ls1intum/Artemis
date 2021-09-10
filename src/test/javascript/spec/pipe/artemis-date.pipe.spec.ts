import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import dayjs from 'dayjs';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('ArtemisDatePipe', () => {
    let pipe: ArtemisDatePipe;
    let translateService: TranslateService;
    const dateTime = dayjs().year(2020).month(3).day(14).hour(9).minute(27).second(3); // 2020-03-14 09:27:03

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [ArtemisDatePipe],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });
        translateService = TestBed.inject(TranslateService);
        pipe = new ArtemisDatePipe(translateService);
    });

    it('Return empty string if given date time is null', () => {
        let localizedDateTime = pipe.transform(null);
        expect(localizedDateTime).to.be.equal('');
        localizedDateTime = pipe.transform(dayjs(null));
        expect(localizedDateTime).to.be.equal('');
    });

    it('Return empty string if given date time is invalid dayjs object', () => {
        const invalidDayjs = dayjs().year(2019).month(2).day(333); // 2019-02-333
        const localizedDateTime = pipe.transform(invalidDayjs);
        expect(localizedDateTime).to.be.equal('');
    });

    describe('en locale', () => {
        beforeEach(() => {
            dateTime.locale('en');
            translateService.currentLang = 'en';
        });

        describe('without seconds', () => {
            it('Should return format equal to "2020-04-14 09:27" with format parameter set to "short"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short');
                const format = 'YYYY-MM-DD HH:mm';
                expect(ArtemisDatePipe.format('en', 'short')).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('2020-04-14 09:27');
            });

            it('Should return format equal to "Apr 14, 2020 09:27" with format parameter set to "long"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long');
                const format = 'll HH:mm';
                expect(ArtemisDatePipe.format('en', 'long')).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('Apr 14, 2020 09:27');
            });

            it('Should return format equal to "2020-04-14" with format parameter set to "short-date"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short-date');
                const format = 'YYYY-MM-DD';
                expect(ArtemisDatePipe.format('en', 'short-date')).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('2020-04-14');
            });

            it('Should return format equal to "Apr 14, 2020" with format parameter set to "long-date"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long-date');
                const format = 'll';
                expect(ArtemisDatePipe.format('en', 'long-date')).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('Apr 14, 2020');
            });

            it('Should return format equal to "09:27" with format parameter set to "time"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'time');
                const format = 'HH:mm';
                expect(ArtemisDatePipe.format('en', 'time')).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('09:27');
            });
        });

        describe('with seconds', () => {
            it('Should return format equal to "2020-04-14 09:27:03" with format parameter set to "short"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short', true);
                const format = 'YYYY-MM-DD HH:mm:ss';
                expect(ArtemisDatePipe.format('en', 'short', true)).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('2020-04-14 09:27:03');
            });

            it('Should return format equal to "Apr 14, 2020 09:27:03" with format parameter set to "long"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long', true);
                const format = 'll HH:mm:ss';
                expect(ArtemisDatePipe.format('en', 'long', true)).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('Apr 14, 2020 09:27:03');
            });

            it('Should return format equal to "2020-04-14" with format parameter set to "short-date"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short-date', true);
                const format = 'YYYY-MM-DD';
                expect(ArtemisDatePipe.format('en', 'short-date', true)).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('2020-04-14');
            });

            it('Should return format equal to "Apr 14, 2020" with format parameter set to "long-date"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long-date', true);
                const format = 'll';
                expect(ArtemisDatePipe.format('en', 'long-date', true)).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('Apr 14, 2020');
            });

            it('Should return format equal to "09:27:03" with format parameter set to "time"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'time', true);
                const format = 'HH:mm:ss';
                expect(ArtemisDatePipe.format('en', 'time', true)).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('09:27:03');
            });
        });
    });

    describe('de locale', () => {
        beforeEach(() => {
            dateTime.locale('de');
            translateService.currentLang = 'de';
        });

        describe('without seconds', () => {
            it('Should return format equal to "14.04.2020 09:27" with format parameter set to "short"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short');
                const format = 'DD.MM.YYYY HH:mm';
                expect(ArtemisDatePipe.format('de', 'short')).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('14.04.2020 09:27');
            });

            it('Should return format equal to "14. Apr. 2020 09:27" with format parameter set to "long"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long');
                const format = 'll HH:mm';
                expect(ArtemisDatePipe.format('de', 'long')).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('14. Apr. 2020 09:27');
            });

            it('Should return format equal to "14.04.2020" with format parameter set to "short-date"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short-date');
                const format = 'DD.MM.YYYY';
                expect(ArtemisDatePipe.format('de', 'short-date')).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('14.04.2020');
            });

            it('Should return format equal to "14. Apr. 2020" with format parameter set to "long-date"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long-date');
                const format = 'll';
                expect(ArtemisDatePipe.format('de', 'long-date')).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('14. Apr. 2020');
            });

            it('Should return format equal to "09:27" with format parameter set to "time"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'time');
                const format = 'HH:mm';
                expect(ArtemisDatePipe.format('de', 'time')).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('09:27');
            });
        });

        describe('with seconds', () => {
            it('Should return format equal to "14.04.2020 09:27:03" with format parameter set to "short"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short', true);
                const format = 'DD.MM.YYYY HH:mm:ss';
                expect(ArtemisDatePipe.format('de', 'short', true)).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('14.04.2020 09:27:03');
            });

            it('Should return format equal to "14. Apr. 2020 09:27:03" with format parameter set to "long"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long', true);
                const format = 'll HH:mm:ss';
                expect(ArtemisDatePipe.format('de', 'long', true)).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('14. Apr. 2020 09:27:03');
            });

            it('Should return format equal to "14.04.2020" with format parameter set to "short-date"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short-date', true);
                const format = 'DD.MM.YYYY';
                expect(ArtemisDatePipe.format('de', 'short-date', true)).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('14.04.2020');
            });

            it('Should return format equal to "14. Apr. 2020" with format parameter set to "long-date"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long-date', true);
                const format = 'll';
                expect(ArtemisDatePipe.format('de', 'long-date', true)).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('14. Apr. 2020');
            });

            it('Should return format equal to "09:27:03" with format parameter set to "time"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'time', true);
                const format = 'HH:mm:ss';
                expect(ArtemisDatePipe.format('de', 'time', true)).to.be.equal(format);
                expect(localizedDateTime).to.be.equal(dateTime.format(format));
                expect(localizedDateTime).to.be.equal('09:27:03');
            });
        });
    });
});
