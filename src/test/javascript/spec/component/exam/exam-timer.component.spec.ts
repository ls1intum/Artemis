import { ComponentFixture, discardPeriodicTasks, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ExamTimerComponent } from 'app/exam/participate/timer/exam-timer.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import * as chai from 'chai';
import * as moment from 'moment';
import { MockPipe } from 'ng-mocks';
import * as sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../test.module';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';

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
            declarations: [ExamTimerComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamTimerComponent);
        component = fixture.componentInstance;
        dateService = TestBed.inject(ArtemisServerDateService);
        component.endDate = inFuture;
    });

    it('should call ngOnInit', () => {
        spyOn(dateService, 'now').and.returnValue(now);
        component.criticalTime = moment.duration(200);
        component.ngOnInit();
        expect(component).to.be.ok;
        expect(component.isCriticalTime).to.be.true;
    });

    it('should update display times', () => {
        let duration = moment.duration(15, 'minutes');
        expect(component.updateDisplayTime(duration)).to.equal('15:00');
        duration = moment.duration(-15, 'seconds');
        expect(component.updateDisplayTime(duration)).to.equal('00:00');
        duration = moment.duration(8, 'minutes');
        expect(component.updateDisplayTime(duration)).to.equal('08:00');
        duration = moment.duration(45, 'seconds');
        expect(component.updateDisplayTime(duration)).to.equal('00:45');
    });

    it('should round to next minute when over 10 minutes', () => {
        let duration = moment.duration(629, 'seconds');
        expect(component.updateDisplayTime(duration)).to.equal('10:00');
        duration = moment.duration(811, 'seconds');
        expect(component.updateDisplayTime(duration)).to.equal('14:00');
    });

    it('should update time in the template correctly', fakeAsync(() => {
        // 30 minutes left
        component.endDate = moment(now).add(30, 'minutes');
        spyOn(dateService, 'now').and.returnValues(moment(now), moment(now), moment(now).add(5, 'minutes'));
        fixture.detectChanges();
        tick();
        let timeShownInTemplate = fixture.debugElement.query(By.css('#displayTime')).nativeElement.innerHTML.trim();
        fixture.detectChanges();
        timeShownInTemplate = fixture.debugElement.query(By.css('#displayTime')).nativeElement.innerHTML.trim();
        expect(timeShownInTemplate).to.equal('30:00');
        tick(100);
        fixture.detectChanges();
        timeShownInTemplate = fixture.debugElement.query(By.css('#displayTime')).nativeElement.innerHTML.trim();
        expect(timeShownInTemplate).to.equal('25:00');
        discardPeriodicTasks();
    }));
});
