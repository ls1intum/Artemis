import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';

const GERMAN_SHORT_DATE_FORMAT = 'DD. MMM. YYYY';

describe('ArtemisDatePipe', () => {
    let pipe: ArtemisDatePipe;
    let translateService: TranslateService;
    const dateTime = dayjs().year(2020).month(3).date(14).hour(9).minute(27).second(3); // 2020-03-14 09:27:03

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [ArtemisDatePipe],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                translateService = TestBed.inject(TranslateService);
                pipe = new ArtemisDatePipe(translateService);
            });
    });

    it('Return empty string if given date time is null or undefined', () => {
        let localizedDateTime = pipe.transform(null);
        expect(localizedDateTime).toBe('');
        localizedDateTime = pipe.transform(undefined);
        expect(localizedDateTime).toBe('');
    });

    it('Return empty string if given date time is invalid dayjs object', () => {
        const invalidDayjs = dayjs('1993-51-11', 'YYYY-MM-DD', true);
        const localizedDateTime = pipe.transform(invalidDayjs);
        expect(localizedDateTime).toBe('');
    });

    describe('en locale', () => {
        beforeEach(() => {
            dateTime.locale('en');
            translateService.currentLang = 'en';
        });

        describe('without seconds', () => {
            it('should return format equal to "2020-04-14 09:27" with format parameter set to "short"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short');
                const format = 'YYYY-MM-DD HH:mm';
                expect(ArtemisDatePipe.format('en', 'short')).toBe(format);
                expect(localizedDateTime).toBe(dateTime.format(format));
                expect(localizedDateTime).toBe('2020-04-14 09:27');
            });

            it('should return format equal to "Apr 14, 2020 09:27" with format parameter set to "long"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long');
                const format = 'll HH:mm';
                expect(ArtemisDatePipe.format('en', 'long')).toBe(format);
                expect(localizedDateTime).toBe(dateTime.format(format));
                expect(localizedDateTime).toBe('Apr 14, 2020 09:27');
            });

            it('should return format equal to "2020-04-14" with format parameter set to "short-date"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short-date');
                const format = 'YYYY-MM-DD';
                expect(ArtemisDatePipe.format('en', 'short-date')).toBe(format);
                expect(localizedDateTime).toBe(dateTime.format(format));
                expect(localizedDateTime).toBe('2020-04-14');
            });

            it('should return format equal to "Apr 14, 2020" with format parameter set to "long-date"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long-date');
                const format = 'll';
                expect(ArtemisDatePipe.format('en', 'long-date')).toBe(format);
                expect(localizedDateTime).toBe(dateTime.format(format));
                expect(localizedDateTime).toBe('Apr 14, 2020');
            });

            it('should return format equal to "09:27" with format parameter set to "time"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'time');
                const format = 'HH:mm';
                expect(ArtemisDatePipe.format('en', 'time')).toBe(format);
                expect(localizedDateTime).toBe(dateTime.format(format));
                expect(localizedDateTime).toBe('09:27');
            });
        });

        describe('with seconds', () => {
            it('should return format equal to "2020-04-14 09:27:03" with format parameter set to "short"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short', true);
                const format = 'YYYY-MM-DD HH:mm:ss';
                expect(ArtemisDatePipe.format('en', 'short', true)).toBe(format);
                expect(localizedDateTime).toBe(dateTime.format(format));
                expect(localizedDateTime).toBe('2020-04-14 09:27:03');
            });

            it('should return format equal to "Apr 14, 2020 09:27:03" with format parameter set to "long"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long', true);
                const format = 'll HH:mm:ss';
                expect(ArtemisDatePipe.format('en', 'long', true)).toBe(format);
                expect(localizedDateTime).toBe(dateTime.format(format));
                expect(localizedDateTime).toBe('Apr 14, 2020 09:27:03');
            });

            it('should return format equal to "2020-04-14" with format parameter set to "short-date"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short-date', true);
                const format = 'YYYY-MM-DD';
                expect(ArtemisDatePipe.format('en', 'short-date', true)).toBe(format);
                expect(localizedDateTime).toBe(dateTime.format(format));
                expect(localizedDateTime).toBe('2020-04-14');
            });

            it('should return format equal to "Apr 14, 2020" with format parameter set to "long-date"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long-date', true);
                const format = 'll';
                expect(ArtemisDatePipe.format('en', 'long-date', true)).toBe(format);
                expect(localizedDateTime).toBe(dateTime.format(format));
                expect(localizedDateTime).toBe('Apr 14, 2020');
            });

            it('should return format equal to "09:27:03" with format parameter set to "time"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'time', true);
                const format = 'HH:mm:ss';
                expect(ArtemisDatePipe.format('en', 'time', true)).toBe(format);
                expect(localizedDateTime).toBe(dateTime.format(format));
                expect(localizedDateTime).toBe('09:27:03');
            });
        });
    });

    describe('de locale', () => {
        beforeEach(() => {
            dateTime.locale('de');
            translateService.currentLang = 'de';
        });

        describe('without seconds', () => {
            it('should return format equal to "14.04.2020 09:27" with format parameter set to "short"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short');
                const format = 'DD.MM.YYYY HH:mm';
                expect(ArtemisDatePipe.format('de', 'short')).toBe(format);
                expect(localizedDateTime).toBe(dateTime.format(format));
                expect(localizedDateTime).toBe('14.04.2020 09:27');
            });

            it('should return format equal to "14. Apr. 2020 09:27" with format parameter set to "long"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long');
                const format = GERMAN_SHORT_DATE_FORMAT + ' HH:mm';
                expect(localizedDateTime).toBe(dateTime.format(format));
                expect(localizedDateTime).toBe('14. Apr. 2020 09:27');
            });

            it('should return format equal to "14.04.2020" with format parameter set to "short-date"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short-date');
                const format = 'DD.MM.YYYY';
                expect(ArtemisDatePipe.format('de', 'short-date')).toBe(format);
                expect(localizedDateTime).toBe(dateTime.format(format));
                expect(localizedDateTime).toBe('14.04.2020');
            });

            it('should return format equal to "14. Apr. 2020" with format parameter set to "long-date"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long-date');
                expect(localizedDateTime).toBe(dateTime.format(GERMAN_SHORT_DATE_FORMAT));
                expect(localizedDateTime).toBe('14. Apr. 2020');
            });

            it('should return format equal to "09:27" with format parameter set to "time"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'time');
                const format = 'HH:mm';
                expect(ArtemisDatePipe.format('de', 'time')).toBe(format);
                expect(localizedDateTime).toBe(dateTime.format(format));
                expect(localizedDateTime).toBe('09:27');
            });
        });

        describe('with seconds', () => {
            it('should return format equal to "14.04.2020 09:27:03" with format parameter set to "short"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short', true);
                const format = 'DD.MM.YYYY HH:mm:ss';
                expect(ArtemisDatePipe.format('de', 'short', true)).toBe(format);
                expect(localizedDateTime).toBe(dateTime.format(format));
                expect(localizedDateTime).toBe('14.04.2020 09:27:03');
            });

            it('should return format equal to "14. Apr. 2020 09:27:03" with format parameter set to "long"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long', true);
                const format = GERMAN_SHORT_DATE_FORMAT + ' HH:mm:ss';
                expect(localizedDateTime).toBe(dateTime.format(format));
                expect(localizedDateTime).toBe('14. Apr. 2020 09:27:03');
            });

            it('should return format equal to "14.04.2020" with format parameter set to "short-date"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short-date', true);
                const format = 'DD.MM.YYYY';
                expect(ArtemisDatePipe.format('de', 'short-date', true)).toBe(format);
                expect(localizedDateTime).toBe(dateTime.format(format));
                expect(localizedDateTime).toBe('14.04.2020');
            });

            it('should return format equal to "14. Apr. 2020" with format parameter set to "long-date"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long-date', true);
                expect(localizedDateTime).toBe(dateTime.format(GERMAN_SHORT_DATE_FORMAT));
                expect(localizedDateTime).toBe('14. Apr. 2020');
            });

            it('should return format equal to "09:27:03" with format parameter set to "time"', () => {
                const localizedDateTime = pipe.transform(dateTime, 'time', true);
                const format = 'HH:mm:ss';
                expect(ArtemisDatePipe.format('de', 'time', true)).toBe(format);
                expect(localizedDateTime).toBe(dateTime.format(format));
                expect(localizedDateTime).toBe('09:27:03');
            });
        });
    });
});
