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

    it('should mark a group at or above half its capacity as well utilized', () => {
        tutorialGroup.capacity = 18;
        tutorialGroup.averageAttendance = 9;
        render(tutorialGroup);

        expect(progressBar()?.severity()).toBe('success');

        const belowHalf = generateExampleTutorialGroup({ id: 2 });
        belowHalf.capacity = 18;
        belowHalf.averageAttendance = 8;
        render(belowHalf);

        expect(progressBar()?.severity()).toBe('primary');
    });

    it('should fall back to the raw attendance when there is no capacity to divide by', () => {
        tutorialGroup.capacity = undefined;
        tutorialGroup.averageAttendance = 7;
        render(tutorialGroup);

        expect(component.utilization()).toBeUndefined();
        expect(progressBar()).toBeUndefined();
        expect(fixture.nativeElement.textContent).toContain('artemisApp.entities.tutorialGroup.averageAttendance');
    });

    it('should draw an empty rail labelled as unknown when no attendance was recorded', () => {
        tutorialGroup.averageAttendance = undefined;
        render(tutorialGroup);

        expect(component.utilization()).toBeUndefined();
        // An empty rail rather than a blank cell, so the column still reads as a column.
        expect(progressBar()?.value()).toBe(0);
        expect(progressBar()?.ariaLabel()).toBe('artemisApp.entities.tutorialGroup.noAttendanceRecorded');
        expect(fixture.nativeElement.textContent).toContain('–');
    });
});
