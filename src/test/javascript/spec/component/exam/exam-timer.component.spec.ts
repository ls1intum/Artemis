import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { ExamTimerComponent } from 'app/exam/participate/timer/exam-timer.component';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as moment from 'moment';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { MockRouter } from '../../helpers/mocks/service/mock-route.service';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { Router } from '@angular/router';
import { MockPipe } from 'ng-mocks';

chai.use(sinonChai);
const expect = chai.expect;

describe('ExamTimerComponent', function () {
    let component: ExamTimerComponent;
    let fixture: ComponentFixture<ExamTimerComponent>;
    let dateService: ArtemisServerDateService;

    const now = moment();
    const inFuture = moment().add(100, 'ms');

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ExamTimerComponent, MockPipe(TranslatePipe)],
            providers: [
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useValue: MockRouter },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamTimerComponent);
        component = fixture.componentInstance;
        dateService = TestBed.inject(ArtemisServerDateService);
        component.endDate = inFuture;
    });

    it('should call ngOnInit', () => {
        spyOn(dateService, 'now').and.returnValue(now);
        component.criticalTime = moment.duration(200);
        expect(component.endDate).to.equal(inFuture);
        component.ngOnInit();
        expect(component).to.be.ok;
        expect(component.isCriticalTime).to.be.true;
    });

    it('should update display times', () => {
        let duration = moment.duration(15, 'minutes');
        expect(component.updateDisplayTime(duration)).to.equal('15 min');
        duration = moment.duration(-15, 'seconds');
        expect(component.updateDisplayTime(duration)).to.equal('00 : 00');
        duration = moment.duration(8, 'minutes');
        expect(component.updateDisplayTime(duration)).to.equal('08 : 00 min');
    });
});
