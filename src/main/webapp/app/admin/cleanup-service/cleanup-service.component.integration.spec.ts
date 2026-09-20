import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { CleanupServiceComponent } from 'app/admin/cleanup-service/cleanup-service.component';
import { DataCleanupService } from 'app/admin/cleanup-service/data-cleanup.service';
import { TumUiDatePickerComponent } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import dayjs from 'dayjs/esm';

describe('CleanupServiceComponent date range integration', () => {
    let fixture: ComponentFixture<CleanupServiceComponent>;
    let component: CleanupServiceComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CleanupServiceComponent],
            providers: [
                {
                    provide: DataCleanupService,
                    useValue: {
                        getLastExecutions: vi.fn().mockReturnValue(of(new HttpResponse({ body: [] }))),
                        getCleanupConfiguration: vi.fn().mockReturnValue(
                            of({
                                gradeRelevantRetentionYears: 5,
                                gradeRelevantCoursesEndedBefore: dayjs('2021-03-04T00:00:00Z'),
                                nonGradeRelevantRetentionYears: 1,
                                nonGradeRelevantCoursesEndedBefore: dayjs('2025-03-04T00:00:00Z'),
                                resetWarningGracePeriodDays: 30,
                                coursesWarnedBefore: dayjs('2026-02-02T00:00:00Z'),
                                oldFeedbackCutoffWeeks: 8,
                                oldFeedbackCoursesEndedBefore: dayjs('2026-01-07T00:00:00Z'),
                                oldSubmissionVersionsCutoffWeeks: 8,
                                oldSubmissionVersionsCoursesEndedBefore: dayjs('2026-01-07T00:00:00Z'),
                                notEnrolledUsersInactivityMonths: 6,
                                usersInactiveBefore: dayjs('2025-09-04T00:00:00Z'),
                                notEnrolledUsersWarningGracePeriodDays: 30,
                                usersWarnedBefore: dayjs('2026-02-02T00:00:00Z'),
                            }),
                        ),
                    },
                },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CleanupServiceComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    function datePickers(operationName: string): TumUiDatePickerComponent[] {
        const row = fixture.debugElement.query(By.css(`[data-testid="cleanup-row-${operationName}"]`));
        return row.queryAll(By.directive(TumUiDatePickerComponent)).map((debugElement) => debugElement.componentInstance as TumUiDatePickerComponent);
    }

    it('should recover when the to-date corrects an invalid date range', () => {
        const operation = component.cleanupOperations()[1];
        const [fromPicker, toPicker] = datePickers(operation.name);
        const invalidFrom = operation.deleteTo!.add(1, 'day');

        // Writing the picker's value model emits its valueChange (the model output) exactly as user input would,
        // so the template's (valueChange)="onDeleteFromChange(operation, $event)" handler runs with the new date.
        fromPicker.value.set(invalidFrom);
        fixture.detectChanges();

        expect(operation.datesValid()).toBe(false);

        const correctedTo = invalidFrom.add(1, 'day');
        toPicker.value.set(correctedTo);
        fixture.detectChanges();

        expect(operation.deleteFrom?.toISOString()).toBe(invalidFrom.toISOString());
        expect(operation.deleteTo?.toISOString()).toBe(correctedTo.toISOString());
        expect(operation.datesValid()).toBe(true);
    });

    it('should recover when the from-date corrects an invalid date range', () => {
        const operation = component.cleanupOperations()[1];
        const [fromPicker, toPicker] = datePickers(operation.name);
        const invalidTo = operation.deleteFrom!.subtract(1, 'day');

        toPicker.value.set(invalidTo);
        fixture.detectChanges();

        expect(operation.datesValid()).toBe(false);

        const correctedFrom = invalidTo.subtract(1, 'day');
        fromPicker.value.set(correctedFrom);
        fixture.detectChanges();

        expect(operation.deleteFrom?.toISOString()).toBe(correctedFrom.toISOString());
        expect(operation.deleteTo?.toISOString()).toBe(invalidTo.toISOString());
        expect(operation.datesValid()).toBe(true);
    });

    it('disables the destructive Execute button when a date field is overwritten with unparseable text', () => {
        const operation = component.cleanupOperations()[1];
        const row = fixture.debugElement.query(By.css(`[data-testid="cleanup-row-${operation.name}"]`));
        const executeButton = () => row.query(By.css('[data-testid="execute-operation"]')).nativeElement as HTMLButtonElement;

        expect(executeButton().disabled).toBe(false);

        const fromInput = row.query(By.css('[data-testid="delete-from-picker"] input')).nativeElement as HTMLInputElement;
        fromInput.value = 'not a date';
        fromInput.dispatchEvent(new Event('input'));
        fixture.detectChanges();

        expect(operation.datesValid()).toBe(true);
        expect(operation.deleteFromValid()).toBe(false);
        expect(executeButton().disabled).toBe(true);
    });

    function rowText(operationName: string, testId: string): string {
        const row = fixture.debugElement.query(By.css(`[data-testid="cleanup-row-${operationName}"]`));
        return (row.query(By.css(`[data-testid="${testId}"]`)).nativeElement as HTMLElement).textContent!.trim();
    }

    it('renders a description under every operation name', () => {
        for (const operation of component.cleanupOperations()) {
            expect(rowText(operation.name, `cleanup-description-${operation.name}`)).toBe(`cleanupService.description.${operation.name}`);
        }
    });

    it('passes the effective cutoff and the matching duration key into the description', () => {
        const row = fixture.debugElement.query(By.css('[data-testid="cleanup-row-warnOldCoursesReset"]'));
        const description = row.query(By.css('[data-testid="cleanup-description-warnOldCoursesReset"]'));
        const values = description.injector.get(TranslateDirective).translateValues()!;

        expect(values['cutoff']).toBe(dayjs('2021-03-04T00:00:00Z').format('MMM D, YYYY'));
        expect(values['period']).toBe('cleanupService.duration.years');
        expect(values['secondaryCutoff']).toBe(dayjs('2025-03-04T00:00:00Z').format('MMM D, YYYY'));
        // A configured retention of one year must use the singular key, so the line does not read "1 years".
        expect(values['secondaryPeriod']).toBe('cleanupService.duration.year');
    });

    it('names the row button after what the operation actually does', () => {
        // "Delete" is wrong for an operation that only emails a warning, or that resets a course while keeping it.
        expect(rowText('warnOldCoursesReset', 'execute-operation')).toBe('entity.action.warn');
        expect(rowText('warnNotEnrolledUsers', 'execute-operation')).toBe('entity.action.warn');
        expect(rowText('resetOldCourses', 'execute-operation')).toBe('entity.action.reset');
        expect(rowText('deleteOrphans', 'execute-operation')).toBe('entity.action.delete');
        expect(rowText('deletePlagiarismCases', 'execute-operation')).toBe('entity.action.delete');
    });
});
