import { ComponentFixture, discardPeriodicTasks, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ExamTimerComponent } from 'app/exam/participate/timer/exam-timer.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import * as chai from 'chai';
import dayjs from 'dayjs';
import { MockPipe } from 'ng-mocks';
import sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../test.module';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('ExamTimerComponent', function () {
    let component: ExamTimerComponent;
    let fixture: ComponentFixture<ExamTimerComponent>;
    let dateService: ArtemisServerDateService;

    const now = dayjs();
    const inFuture = dayjs().add(100, 'ms');

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ExamTimerComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamTimerComponent);
        component = fixture.componentInstance;
        dateService = TestBed.inject(ArtemisServerDateService);
        component.endDate = inFuture;
    });

    it('should call ngOnInit', () => {
        jest.spyOn(dateService, 'now').mockReturnValue(now);
        component.criticalTime = dayjs.duration(200);
        component.ngOnInit();
        expect(component).to.be.ok;
        expect(component.isCriticalTime).to.be.true;
    });

    it('should update display times', () => {
        let duration = dayjs.duration(15, 'minutes');
        expect(component.updateDisplayTime(duration)).to.equal('15min');
        duration = dayjs.duration(-15, 'seconds');
        expect(component.updateDisplayTime(duration)).to.equal('0min 0s');
        duration = dayjs.duration(8, 'minutes');
        expect(component.updateDisplayTime(duration)).to.equal('8min 0s');
        duration = dayjs.duration(45, 'seconds');
        expect(component.updateDisplayTime(duration)).to.equal('0min 45s');
    });

    it('should round down to next minute when over 10 minutes', () => {
        let duration = dayjs.duration(629, 'seconds');
        expect(component.updateDisplayTime(duration)).to.equal('10min');
        duration = dayjs.duration(811, 'seconds');
        expect(component.updateDisplayTime(duration)).to.equal('13min');
    });

    it('should update time in the template correctly', fakeAsync(() => {
        // 30 minutes left
        component.endDate = dayjs(now).add(30, 'minutes');
        jest.spyOn(dateService, 'now').mockReturnValueOnce(dayjs(now)).mockReturnValueOnce(dayjs(now)).mockReturnValueOnce(dayjs(now).add(5, 'minutes'));
        fixture.detectChanges();
        tick();
        let timeShownInTemplate = fixture.debugElement.query(By.css('#displayTime')).nativeElement.innerHTML.trim();
        fixture.detectChanges();
        timeShownInTemplate = fixture.debugElement.query(By.css('#displayTime')).nativeElement.innerHTML.trim();
        expect(timeShownInTemplate).to.equal('30min');
        tick(100);
        fixture.detectChanges();
        timeShownInTemplate = fixture.debugElement.query(By.css('#displayTime')).nativeElement.innerHTML.trim();
        expect(timeShownInTemplate).to.equal('25min');
        discardPeriodicTasks();
    }));
});
