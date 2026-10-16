import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';
import { MockPipe } from 'ng-mocks';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TumAetUiDatePickerComponent } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TimelineStatus } from 'app/shared-ui/timeline/timeline.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ExamTimelineComponent } from './exam-timeline.component';

describe('ExamTimelineComponent', () => {
    let fixture: ComponentFixture<ExamTimelineComponent>;
    let component: ExamTimelineComponent;

    const pickers = () => fixture.debugElement.queryAll(By.directive(TumAetUiDatePickerComponent));
    const picker = (index: number) => pickers()[index].componentInstance as TumAetUiDatePickerComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExamTimelineComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .overrideComponent(ExamTimelineComponent, { set: { imports: [TumAetUiDatePickerComponent, MockPipe(ArtemisTranslatePipe, (key: string) => key)] } })
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
    });

    it('should use the working-window labels for a test exam', () => {
        fixture.componentRef.setInput('testExam', true);

        expect(component.timelineItems().map((item) => item.labelStringKey)).toEqual([
            'artemisApp.examManagement.visibleDate',
            'artemisApp.examManagement.testExam.startDate',
            'artemisApp.examManagement.testExam.endDate',
        ]);
    });

    it('should warn when the exam becomes visible more than four hours before it starts', () => {
        const visibleDate = dayjs('2026-01-01T10:00:00Z');
        component.visibleDate.set(visibleDate);
        component.startDate.set(visibleDate.add(240, 'minutes'));

        expect(component.timelineItems()[0].warningStringKey?.()).toBeUndefined();

        component.startDate.set(visibleDate.add(241, 'minutes'));

        expect(component.timelineItems()[0].warningStringKey?.()).toBe('entity.visibleDateWarningError');

        component.visibleDate.set(undefined);

        expect(component.timelineItems()[0].warningStringKey?.()).toBeUndefined();
    });

    it('should render one date field per date with a stable id', () => {
        fixture.detectChanges();

        expect(pickers()).toHaveLength(3);
        expect(pickers().map((debugElement) => (debugElement.componentInstance as TumAetUiDatePickerComponent).inputId())).toEqual([
            'exam-visibleDate',
            'exam-startDate',
            'exam-endDate',
        ]);
        expect(fixture.nativeElement.querySelector('#exam-startDate')).toBeTruthy();
    });

    it('should write a date chosen in a field back to the matching model', () => {
        fixture.detectChanges();
        const date = dayjs('2026-03-01T12:00:00Z');

        picker(1).value.set(date);
        fixture.detectChanges();

        expect(component.startDate()).toBe(date);
        expect(component.visibleDate()).toBeUndefined();
    });

    it('should show the dates of the models in the fields', () => {
        const date = dayjs('2026-03-01T12:00:00Z');
        component.endDate.set(date);
        fixture.detectChanges();

        expect(picker(2).value()).toBe(date);
        expect(picker(0).value()).toBeUndefined();
    });

    it('should report an incomplete timeline as invalid and empty, and a complete ascending one as valid', () => {
        fixture.detectChanges();
        const statuses: TimelineStatus[] = [];
        component.timelineStatusChange.subscribe((status) => statuses.push(status));

        expect(component.timelineStatus().valid).toBe(false);
        expect(component.timelineStatus().empty).toBe(true);
        expect(component.timelineStatus().invalidItems.map((item) => item.reasonKey)).toEqual(Array(3).fill('artemisApp.exercise.form.timeline.required'));

        const base = dayjs('2026-03-01T08:00:00Z');
        component.visibleDate.set(base);
        component.startDate.set(base.add(1, 'hour'));
        component.endDate.set(base.add(3, 'hour'));
        fixture.detectChanges();

        expect(component.timelineStatus()).toEqual({ valid: true, empty: false, invalidItems: [] });
        expect(statuses.at(-1)).toEqual({ valid: true, empty: false, invalidItems: [] });
    });

    it('should report a date that is not after the preceding date as invalid', () => {
        const base = dayjs('2026-03-01T08:00:00Z');
        component.visibleDate.set(base);
        component.startDate.set(base);
        component.endDate.set(base.add(3, 'hour'));
        fixture.detectChanges();

        const status = component.timelineStatus();
        expect(status.valid).toBe(false);
        expect(status.empty).toBe(false);
        expect(status.invalidItems).toEqual([
            { labelStringKey: 'artemisApp.examManagement.startDate', reasonKey: 'artemisApp.exercise.form.timeline.strictOrder', dateName: 'artemisApp.examManagement.startDate' },
        ]);
        expect(fixture.nativeElement.querySelector('[data-testid="exam-startDate-info"]')).toBeTruthy();
    });

    it('should report a typed text that is not a date as invalid', () => {
        const base = dayjs('2026-03-01T08:00:00Z');
        component.visibleDate.set(base);
        component.startDate.set(base.add(1, 'hour'));
        component.endDate.set(base.add(3, 'hour'));
        fixture.detectChanges();

        pickers()[1].triggerEventHandler('inputValidityChange', false);
        fixture.detectChanges();

        expect(component.timelineStatus().valid).toBe(false);
        expect(component.timelineStatus().invalidItems.map((item) => item.reasonKey)).toEqual(['artemisApp.exercise.form.timeline.invalidInput']);

        pickers()[1].triggerEventHandler('inputValidityChange', true);
        fixture.detectChanges();

        expect(component.timelineStatus().valid).toBe(true);
    });

    it('should only paint a missing date as an error after its field was left', () => {
        fixture.detectChanges();

        expect(picker(0).invalid()).toBe(false);
        expect(fixture.nativeElement.querySelector('[data-testid="exam-visibleDate-info"]')).toBeNull();

        pickers()[0].triggerEventHandler('touch');
        fixture.detectChanges();

        expect(picker(0).invalid()).toBe(true);
        expect(fixture.nativeElement.querySelector('[data-testid="exam-visibleDate-info"]')).toBeTruthy();
    });

    it('should show the warning without painting the field as invalid', () => {
        const visibleDate = dayjs('2026-01-01T10:00:00Z');
        component.visibleDate.set(visibleDate);
        component.startDate.set(visibleDate.add(300, 'minutes'));
        component.endDate.set(visibleDate.add(600, 'minutes'));
        fixture.detectChanges();

        expect(component.timelineStatus().valid).toBe(true);
        expect(picker(0).invalid()).toBe(false);
        expect(fixture.nativeElement.querySelector('[data-testid="exam-visibleDate-info"]')).toBeTruthy();
    });

    it('should emit when a working-time date changes', () => {
        fixture.detectChanges();
        const emitSpy = vi.spyOn(component.datesChanged, 'emit');

        component.startDate.set(dayjs());
        fixture.detectChanges();

        expect(emitSpy).toHaveBeenCalledOnce();
    });
});
