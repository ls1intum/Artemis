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
        hour: 15,
        minute: 27,
        seconds: 33,
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

        describe('long format', () => {
            it('Should return format equal to "Apr 14, 2020 3:27:33 PM" when no parameter is set', () => {
                const localizedDateTime = pipe.transform(dateTime);
                expect(localizedDateTime).to.be.equal(dateTime.format('ll LTS'));
                expect(localizedDateTime).to.be.equal('Apr 14, 2020 3:27:33 PM');
            });

            it('Should return format equal to "Apr 14, 2020 3:27:33 PM" with parameter set { format: "long" }', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long');
                expect(localizedDateTime).to.be.equal(dateTime.format('ll LTS'));
                expect(localizedDateTime).to.be.equal('Apr 14, 2020 3:27:33 PM');
            });

            it('Should return format equal to "Apr 14, 2020 3:27:33 PM" with parameter set { format: "long", time: true }', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long');
                expect(localizedDateTime).to.be.equal(dateTime.format('ll LTS'));
                expect(localizedDateTime).to.be.equal('Apr 14, 2020 3:27:33 PM');
            });

            it('Should return format equal to "Apr 14, 2020" with parameter set { format: "long", time: false }', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long', false);
                expect(localizedDateTime).to.be.equal(dateTime.format('ll'));
                expect(localizedDateTime).to.be.equal('Apr 14, 2020');
            });

            it('Should return format equal to "Apr 14, 2020 3:27:33 PM" with parameter set { format: "long", time: true, seconds: true }', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long', true, true);
                expect(localizedDateTime).to.be.equal(dateTime.format('ll LTS'));
                expect(localizedDateTime).to.be.equal('Apr 14, 2020 3:27:33 PM');
            });

            it('Should return format equal to "Apr 14, 2020" with parameter set { format: "long", time: false, seconds: true }', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long', false, true);
                expect(localizedDateTime).to.be.equal(dateTime.format('ll'));
                expect(localizedDateTime).to.be.equal('Apr 14, 2020');
            });

            it('Should return format equal to "Apr 14, 2020 3:27 PM" with parameter set { format: "long", time: true, seconds: false }', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long', true, false);
                expect(localizedDateTime).to.be.equal(dateTime.format('ll LT'));
                expect(localizedDateTime).to.be.equal('Apr 14, 2020 3:27 PM');
            });

            it('Should return format equal to "Apr 14, 2020" with parameter set { format: "long", time: false, seconds: false }', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long', false, false);
                expect(localizedDateTime).to.be.equal(dateTime.format('ll'));
                expect(localizedDateTime).to.be.equal('Apr 14, 2020');
            });
        });

        describe('short format', () => {
            it('Should return format equal to "14/4/20 15:27:33" with parameter set { format: "short" }', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short');
                expect(localizedDateTime).to.be.equal(dateTime.format('D/M/YY HH:mm:ss'));
                expect(localizedDateTime).to.be.equal('14/4/20 15:27:33');
            });

            it('Should return format equal to "14/4/20 15:27:33" with parameter set { format: "short", time: true }', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short');
                expect(localizedDateTime).to.be.equal(dateTime.format('D/M/YY HH:mm:ss'));
                expect(localizedDateTime).to.be.equal('14/4/20 15:27:33');
            });

            it('Should return format equal to "14/4/20" with parameter set { format: "short", time: false }', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short', false);
                expect(localizedDateTime).to.be.equal(dateTime.format('D/M/YY'));
                expect(localizedDateTime).to.be.equal('14/4/20');
            });

            it('Should return format equal to "14/4/20 15:27:33" with parameter set { format: "short", time: true, seconds: true }', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short', true, true);
                expect(localizedDateTime).to.be.equal(dateTime.format('D/M/YY HH:mm:ss'));
                expect(localizedDateTime).to.be.equal('14/4/20 15:27:33');
            });

            it('Should return format equal to "14/4/20" with parameter set { format: "short", time: false, seconds: true }', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short', false, true);
                expect(localizedDateTime).to.be.equal(dateTime.format('D/M/YY'));
                expect(localizedDateTime).to.be.equal('14/4/20');
            });

            it('Should return format equal to "14/4/20 15:27" with parameter set { format: "short", time: true, seconds: false }', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short', true, false);
                expect(localizedDateTime).to.be.equal(dateTime.format('D/M/YY HH:mm'));
                expect(localizedDateTime).to.be.equal('14/4/20 15:27');
            });

            it('Should return format equal to "14/4/20" with parameter set { format: "short", time: false, seconds: false }', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short', false, false);
                expect(localizedDateTime).to.be.equal(dateTime.format('D/M/YY'));
                expect(localizedDateTime).to.be.equal('14/4/20');
            });
        });
    });

    describe('de locale', () => {
        beforeEach(() => {
            dateTime.locale('de');
            translateService.currentLang = 'de';
        });

        describe('long format', () => {
            it('Should return format equal to "14. Apr. 2020 15:27:33" when no parameter is set', () => {
                const localizedDateTime = pipe.transform(dateTime);
                expect(localizedDateTime).to.be.equal(dateTime.format('ll LTS'));
                expect(localizedDateTime).to.be.equal('14. Apr. 2020 15:27:33');
            });

            it('Should return format equal to "14. Apr. 2020 15:27:33" with parameter set { format: "long" }', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long');
                expect(localizedDateTime).to.be.equal(dateTime.format('ll LTS'));
                expect(localizedDateTime).to.be.equal('14. Apr. 2020 15:27:33');
            });

            it('Should return format equal to "14. Apr. 2020 15:27:33" with parameter set { format: "long", time: true }', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long');
                expect(localizedDateTime).to.be.equal(dateTime.format('ll LTS'));
                expect(localizedDateTime).to.be.equal('14. Apr. 2020 15:27:33');
            });

            it('Should return format equal to "14. Apr. 2020" with parameter set { format: "long", time: false }', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long', false);
                expect(localizedDateTime).to.be.equal(dateTime.format('ll'));
                expect(localizedDateTime).to.be.equal('14. Apr. 2020');
            });

            it('Should return format equal to "14. Apr. 2020 15:27:33" with parameter set { format: "long", time: true, seconds: true }', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long', true, true);
                expect(localizedDateTime).to.be.equal(dateTime.format('ll LTS'));
                expect(localizedDateTime).to.be.equal('14. Apr. 2020 15:27:33');
            });

            it('Should return format equal to "14. Apr. 2020" with parameter set { format: "long", time: false, seconds: true }', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long', false, true);
                expect(localizedDateTime).to.be.equal(dateTime.format('ll'));
                expect(localizedDateTime).to.be.equal('14. Apr. 2020');
            });

            it('Should return format equal to "14. Apr. 2020 15:27" with parameter set { format: "long", time: true, seconds: false }', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long', true, false);
                expect(localizedDateTime).to.be.equal(dateTime.format('ll LT'));
                expect(localizedDateTime).to.be.equal('14. Apr. 2020 15:27');
            });

            it('Should return format equal to "14. Apr. 2020" with parameter set { format: "long", time: false, seconds: false }', () => {
                const localizedDateTime = pipe.transform(dateTime, 'long', false, false);
                expect(localizedDateTime).to.be.equal(dateTime.format('ll'));
                expect(localizedDateTime).to.be.equal('14. Apr. 2020');
            });
        });

        describe('short format', () => {
            it('Should return format equal to "14.4.20 15:27:33" with parameter set { format: "short" }', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short');
                expect(localizedDateTime).to.be.equal(dateTime.format('D.M.YY HH:mm:ss'));
                expect(localizedDateTime).to.be.equal('14.4.20 15:27:33');
            });

            it('Should return format equal to "14.4.20 15:27:33" with parameter set { format: "short", time: true }', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short');
                expect(localizedDateTime).to.be.equal(dateTime.format('D.M.YY HH:mm:ss'));
                expect(localizedDateTime).to.be.equal('14.4.20 15:27:33');
            });

            it('Should return format equal to "14.4.20" with parameter set { format: "short", time: false }', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short', false);
                expect(localizedDateTime).to.be.equal(dateTime.format('D.M.YY'));
                expect(localizedDateTime).to.be.equal('14.4.20');
            });

            it('Should return format equal to "14.4.20 15:27:33" with parameter set { format: "short", time: true, seconds: true }', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short', true, true);
                expect(localizedDateTime).to.be.equal(dateTime.format('D.M.YY HH:mm:ss'));
                expect(localizedDateTime).to.be.equal('14.4.20 15:27:33');
            });

            it('Should return format equal to "14.4.20" with parameter set { format: "short", time: false, seconds: true }', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short', false, true);
                expect(localizedDateTime).to.be.equal(dateTime.format('D.M.YY'));
                expect(localizedDateTime).to.be.equal('14.4.20');
            });

            it('Should return format equal to "14.4.20 15:27" with parameter set { format: "short", time: true, seconds: false }', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short', true, false);
                expect(localizedDateTime).to.be.equal(dateTime.format('D.M.YY HH:mm'));
                expect(localizedDateTime).to.be.equal('14.4.20 15:27');
            });

            it('Should return format equal to "14.4.20" with parameter set { format: "short", time: false, seconds: false }', () => {
                const localizedDateTime = pipe.transform(dateTime, 'short', false, false);
                expect(localizedDateTime).to.be.equal(dateTime.format('D.M.YY'));
                expect(localizedDateTime).to.be.equal('14.4.20');
            });
        });
    });
});
