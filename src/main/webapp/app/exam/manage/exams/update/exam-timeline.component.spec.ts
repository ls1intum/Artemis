import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';
import { MockComponent } from 'ng-mocks';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TimelineComponent } from 'app/shared-ui/timeline/timeline.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ExamTimelineComponent } from './exam-timeline.component';

describe('ExamTimelineComponent', () => {
    let fixture: ComponentFixture<ExamTimelineComponent>;
    let component: ExamTimelineComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExamTimelineComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .overrideComponent(ExamTimelineComponent, { set: { imports: [MockComponent(TimelineComponent)] } })
            .compileComponents();

        fixture = TestBed.createComponent(ExamTimelineComponent);
        component = fixture.componentInstance;
        await fixture.whenStable();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should expose the required exam dates in chronological order', () => {
        expect(component.timelineItems().map((item) => item.labelStringKey)).toEqual([
            'artemisApp.examManagement.visibleDate',
            'artemisApp.examManagement.startDate',
            'artemisApp.examManagement.endDate',
        ]);
        expect(component.timelineItems().map((item) => item.date)).toEqual([component.visibleDate, component.startDate, component.endDate]);
        expect(component.timelineItems().every((item) => item.kind === 'required')).toBe(true);
    });

    it('should use the working-window labels for a test exam', () => {
        fixture.componentRef.setInput('testExam', true);

        expect(component.timelineItems().map((item) => item.labelStringKey)).toEqual([
            'artemisApp.examManagement.visibleDate',
            'artemisApp.examManagement.testExam.startDate',
            'artemisApp.examManagement.testExam.endDate',
        ]);
    });

    it('should forward timeline status changes', () => {
        fixture.detectChanges();
        const emitSpy = vi.spyOn(component.timelineStatusChange, 'emit');
        const timeline = fixture.debugElement.query(By.directive(TimelineComponent)).componentInstance as TimelineComponent;
        const status = { valid: false, empty: true };

        timeline.timelineStatusChange.emit(status);

        expect(emitSpy).toHaveBeenCalledExactlyOnceWith(status);
    });

    it('should emit when a working-time date changes', () => {
        fixture.detectChanges();
        const emitSpy = vi.spyOn(component.datesChanged, 'emit');

        component.startDate.set(dayjs());
        fixture.detectChanges();

        expect(emitSpy).toHaveBeenCalledOnce();
    });
});
