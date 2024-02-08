import dayjs from 'dayjs/esm';
import { FormsModule } from '@angular/forms';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Exam } from 'app/entities/exam.model';
import { WorkingTimeControlComponent } from 'app/exam/shared/working-time-control/working-time-control.component';

const createTestExam = (duration: number) => ({ workingTime: duration, startDate: dayjs.unix(0), endDate: dayjs.unix(duration) }) as Exam;

describe('WorkingTimeControlComponent', () => {
    let component: WorkingTimeControlComponent;
    let fixture: ComponentFixture<WorkingTimeControlComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FormsModule],
            declarations: [WorkingTimeControlComponent],
        }).compileComponents();
        fixture = TestBed.createComponent(WorkingTimeControlComponent);
        component = fixture.componentInstance;
    });

    const expectDuration = (hours: number, minutes: number, seconds: number) => {
        expect(component.workingTime.hours).toBe(hours);
        expect(component.workingTime.minutes).toBe(minutes);
        expect(component.workingTime.seconds).toBe(seconds);
    };

    it('should parse working time seconds to duration', () => {
        // act
        component.workingTimeSeconds = 3600;
        fixture.detectChanges();

        // assert
        expectDuration(1, 0, 0);
    });

    it('should ignore relative working time extension when exam is not present', () => {
        // act
        component.workingTimeSeconds = 3600;
        fixture.detectChanges();

        // assert
        expect(component.workingTime.percent).toBe(0);
    });

    it('should parse working time seconds to relative working time extension', () => {
        // act
        component.workingTimeSeconds = 7200;
        component.exam = createTestExam(3600);
        fixture.detectChanges();

        // assert
        expect(component.workingTime.percent).toBe(100);
    });

    it('should not show relative working time extension when exam is not present', async () => {
        // act
        fixture.detectChanges();
        await fixture.whenStable();

        // assert
        expect(fixture.nativeElement.querySelector('#workingTimePercent')).toBeNull();
    });

    it('should not show relative working time if `relative` is false', async () => {
        // act
        component.relative = false;
        component.exam = {} as Exam;
        fixture.detectChanges();
        await fixture.whenStable();

        // assert
        expect(fixture.nativeElement.querySelector('#workingTimePercent')).toBeNull();
    });

    it('should show relative working time if exam is present and `relative` is true', async () => {
        // act
        component.relative = true;
        component.exam = {} as Exam;
        fixture.detectChanges();
        await fixture.whenStable();

        // assert
        expect(fixture.nativeElement.querySelector('#workingTimePercent')).not.toBeNull();
    });

    it('should disable inputs when `disabled` is true', async () => {
        // act
        component.disabled = true;
        component.exam = {} as Exam;
        component.relative = true;
        fixture.detectChanges();
        await fixture.whenStable();

        // assert
        expect(fixture.nativeElement.querySelector('#workingTimeHours').disabled).toBeTruthy();
        expect(fixture.nativeElement.querySelector('#workingTimeMinutes').disabled).toBeTruthy();
        expect(fixture.nativeElement.querySelector('#workingTimeSeconds').disabled).toBeTruthy();
        expect(fixture.nativeElement.querySelector('#workingTimePercent').disabled).toBeTruthy();
    });

    it('should update the percent difference when the absolute working time changes', () => {
        // arrange
        component.exam = createTestExam(7200);

        // act & assert
        component.workingTime.hours = 4;
        component.onDurationChanged();
        fixture.detectChanges();
        expect(component.workingTime.percent).toBe(100);

        component.workingTime.hours = 3;
        component.onDurationChanged();
        fixture.detectChanges();
        expect(component.workingTime.percent).toBe(50);

        component.workingTime.hours = 0;
        component.onDurationChanged();
        fixture.detectChanges();
        expect(component.workingTime.percent).toBe(-100);

        // small change, not a full percent
        component.workingTime.hours = 2;
        component.workingTime.seconds = 12;
        component.onDurationChanged();
        fixture.detectChanges();
        expect(component.workingTime.percent).toBe(0.17);
    });

    it('should update the absolute working time when changing the percent difference', () => {
        // arrange
        component.exam = createTestExam(7200);

        // act & assert
        component.workingTime.percent = 26;
        component.onPercentChanged();
        fixture.detectChanges();
        expectDuration(2, 31, 12);

        component.workingTime.percent = -100;
        component.onPercentChanged();
        fixture.detectChanges();
        expectDuration(0, 0, 0);
    });
});
