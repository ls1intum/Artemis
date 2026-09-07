import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { generateExampleTutorialGroup } from 'test/helpers/sample/tutorialgroup/tutorialGroupExampleModels';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialGroupUtilizationIndicatorComponent } from 'app/tutorialgroup/manage/tutorial-group-utilization-indicator/tutorial-group-utilization-indicator.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TumUiProgressBarComponent } from '@tumaet/ui-angular';

describe('TutorialGroupUtilizationIndicatorComponent', () => {
    let component: TutorialGroupUtilizationIndicatorComponent;
    let fixture: ComponentFixture<TutorialGroupUtilizationIndicatorComponent>;
    let tutorialGroup: TutorialGroup;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TutorialGroupUtilizationIndicatorComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialGroupUtilizationIndicatorComponent);
        component = fixture.componentInstance;
        tutorialGroup = generateExampleTutorialGroup({});
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    function render(group: TutorialGroup): void {
        fixture.componentRef.setInput('tutorialGroup', group);
        fixture.detectChanges();
    }

    function progressBar(): TumUiProgressBarComponent | undefined {
        return fixture.debugElement.query(By.directive(TumUiProgressBarComponent))?.componentInstance;
    }

    it('should express the average attendance as a percentage of the capacity', () => {
        tutorialGroup.capacity = 18;
        tutorialGroup.averageAttendance = 6;
        render(tutorialGroup);

        expect(component.utilization()).toBe(33);
        expect(progressBar()?.value()).toBe(33);
        expect(fixture.nativeElement.textContent).toContain('33%');
    });

    // Attendance out of a capacity of 100, so the percentage equals the attendance and the bands read directly.
    it.each([
        { attendance: 0, expected: 'danger' },
        { attendance: 24, expected: 'danger' },
        { attendance: 25, expected: 'warn' },
        { attendance: 49, expected: 'warn' },
        { attendance: 50, expected: 'success' },
        { attendance: 100, expected: 'success' },
    ])('should colour the bar $expected at $attendance% utilization', ({ attendance, expected }) => {
        const group = generateExampleTutorialGroup({ id: attendance + 1 });
        group.capacity = 100;
        group.averageAttendance = attendance;
        render(group);

        expect(progressBar()?.severity()).toBe(expected);
    });

    it('should fall back to the raw attendance when there is no capacity to divide by', () => {
        tutorialGroup.capacity = undefined;
        tutorialGroup.averageAttendance = 7;
        render(tutorialGroup);

        expect(component.utilization()).toBeUndefined();
        expect(progressBar()).toBeUndefined();
        expect(fixture.nativeElement.textContent).toContain('artemisApp.entities.tutorialGroup.averageAttendance');
    });

    // A bar would carry aria-valuenow="0", announcing an unknown utilization as a measured zero.
    it('should state that nothing was recorded instead of drawing a bar when no attendance was recorded', () => {
        tutorialGroup.averageAttendance = undefined;
        render(tutorialGroup);

        expect(component.utilization()).toBeUndefined();
        expect(progressBar()).toBeUndefined();
        expect(fixture.nativeElement.textContent).toContain('–');
        expect(fixture.nativeElement.querySelector('.sr-only').textContent).toContain('artemisApp.entities.tutorialGroup.noAttendanceRecorded');
    });
});
