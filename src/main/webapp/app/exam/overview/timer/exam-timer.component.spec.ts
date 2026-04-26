import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ExamTimerComponent } from 'app/exam/overview/timer/exam-timer.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisServerDateService } from 'app/shared/service/server-date.service';
import dayjs from 'dayjs/esm';
import { MockPipe } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { provideHttpClient } from '@angular/common/http';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('ExamTimerComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ExamTimerComponent;
    let fixture: ComponentFixture<ExamTimerComponent>;
    let dateService: ArtemisServerDateService;

    const now = dayjs();
    const inFuture = dayjs().add(100, 'ms');

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ExamTimerComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, provideHttpClient()],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamTimerComponent);
        component = fixture.componentInstance;
        dateService = TestBed.inject(ArtemisServerDateService);
        fixture.componentRef.setInput('endDate', inFuture);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should call ngOnInit', () => {
        vi.spyOn(dateService, 'now').mockReturnValue(now);
        fixture.componentRef.setInput('criticalTime', dayjs.duration(200));
        component.ngOnInit();
        expect(component).not.toBeNull();
        expect(component.isCriticalTime()).toBe(true);
    });

    it('should update display times', () => {
        let duration = dayjs.duration(15, 'minutes');
        expect(component.updateDisplayTime(duration)).toBe('15min');
        duration = dayjs.duration(-15, 'seconds');
        expect(component.updateDisplayTime(duration)).toBe('0min 0s');
        duration = dayjs.duration(8, 'minutes');
        expect(component.updateDisplayTime(duration)).toBe('8min 0s');
        duration = dayjs.duration(45, 'seconds');
        expect(component.updateDisplayTime(duration)).toBe('0min 45s');
    });

    it('should round down to next minute when over 10 minutes', () => {
        let duration = dayjs.duration(629, 'seconds');
        expect(component.updateDisplayTime(duration)).toBe('10min');
        duration = dayjs.duration(811, 'seconds');
        expect(component.updateDisplayTime(duration)).toBe('13min');
    });

    it('should update time in the template correctly', () => {
        const endDate = dayjs(now).add(30, 'minutes');
        fixture.componentRef.setInput('endDate', endDate);
        // After 0 minutes from now, 30 minutes remain
        let remaining = dayjs.duration(endDate.diff(dayjs(now)));
        expect(component.updateDisplayTime(remaining)).toBe('30min');
        // After 5 minutes elapsed, 25 minutes remain
        remaining = dayjs.duration(endDate.diff(dayjs(now).add(5, 'minutes')));
        expect(component.updateDisplayTime(remaining)).toBe('25min');
        component.ngOnDestroy();
    });
});
