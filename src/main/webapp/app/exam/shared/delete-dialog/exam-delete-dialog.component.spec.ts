import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ExamDeleteDialogComponent } from 'app/exam/shared/delete-dialog/exam-delete-dialog.component';
import { ActionType, EntitySummary } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';

describe('ExamDeleteDialogComponent', () => {
    let fixture: ComponentFixture<ExamDeleteDialogComponent>;
    let component: ExamDeleteDialogComponent;
    let alertService: AlertService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AlertService, useClass: MockAlertService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamDeleteDialogComponent);
        component = fixture.componentInstance;
        alertService = TestBed.inject(AlertService);
        fixture.componentRef.setInput('deleteQuestion', 'question.key');
        fixture.componentRef.setInput('entityTitle', 'My exam');
    });

    afterEach(() => {
        vi.restoreAllMocks();
        document.body.querySelectorAll('.cdk-overlay-container').forEach((container) => container.replaceChildren());
    });

    function open(): void {
        component.visible.set(true);
        fixture.detectChanges();
    }

    it('should use the title of the entity in the question', () => {
        fixture.componentRef.setInput('translateValues', { other: 1 });
        expect(component.translationValues()).toEqual({ other: 1, title: 'My exam' });
    });

    it('should pick the header and the label of the confirming button from the action', () => {
        fixture.componentRef.setInput('actionType', ActionType.Reset);
        expect(component.headerKey()).toBe('entity.reset.title');
        expect(component.actionKey()).toBe('entity.action.reset');
        expect(component.isDestructive()).toBe(true);

        fixture.componentRef.setInput('actionType', ActionType.EndNow);
        expect(component.headerKey()).toBe('entity.endNow.title');
        expect(component.isDestructive()).toBe(false);
    });

    it('should ask for the name only when a confirmation text is given', () => {
        expect(component.needsEntityName()).toBe(false);
        fixture.componentRef.setInput('deleteConfirmationText', 'confirm.key');
        expect(component.needsEntityName()).toBe(true);
    });

    it('should ask for the name only for selected extra checks when requested', () => {
        fixture.componentRef.setInput('deleteConfirmationText', 'confirm.key');
        fixture.componentRef.setInput('additionalChecks', { deleteEverything: 'check.key' });
        fixture.componentRef.setInput('requireConfirmationOnlyForAdditionalChecks', true);
        open();
        expect(component.needsEntityName()).toBe(false);

        component.setCheck('deleteEverything', true);
        expect(component.needsEntityName()).toBe(true);
    });

    it('should start every opening with unselected checks and an empty name', () => {
        fixture.componentRef.setInput('additionalChecks', { deleteEverything: 'check.key' });
        open();
        component.setCheck('deleteEverything', true);
        component.confirmEntityName = 'My exam';

        component.clear();
        fixture.detectChanges();
        open();

        expect(component.checkValues()).toEqual({ deleteEverything: false });
        expect(component.confirmEntityName).toBe('');
    });

    it('should list the summary of the entity', () => {
        const summary: EntitySummary = { 'summary.students': 5, 'summary.hidden': undefined };
        fixture.componentRef.setInput('fetchEntitySummary', of(summary));
        open();

        expect(component.isLoadingSummary()).toBe(false);
        expect(component.summaryKeys()).toEqual(['summary.students']);
    });

    it('should show that the summary is loading until it arrives', () => {
        const summary$ = new Subject<EntitySummary>();
        fixture.componentRef.setInput('fetchEntitySummary', summary$);
        open();
        expect(component.isLoadingSummary()).toBe(true);

        summary$.next({ 'summary.students': 1 });
        expect(component.isLoadingSummary()).toBe(false);
    });

    it('should alert and stop loading when the summary cannot be fetched', () => {
        const errorSpy = vi.spyOn(alertService, 'error');
        fixture.componentRef.setInput(
            'fetchEntitySummary',
            throwError(() => new HttpErrorResponse({ status: 500, statusText: 'failed' })),
        );
        open();

        expect(component.isLoadingSummary()).toBe(false);
        expect(component.summaryKeys()).toEqual([]);
        expect(errorSpy).toHaveBeenCalledWith('error.unexpectedError', { error: expect.any(String) });
    });

    it('should close the dialog and report the extra checks on confirm', () => {
        fixture.componentRef.setInput('additionalChecks', { deleteEverything: 'check.key' });
        const confirmed: Record<string, boolean>[] = [];
        component.confirmed.subscribe((checks) => confirmed.push(checks));
        open();
        component.setCheck('deleteEverything', true);

        component.confirm();

        expect(component.visible()).toBe(false);
        expect(confirmed).toEqual([{ deleteEverything: true }]);
    });

    it('should show the error of a failed action as an alert and ignore a success', () => {
        const error$ = new Subject<string>();
        fixture.componentRef.setInput('dialogError', error$);
        const errorSpy = vi.spyOn(alertService, 'error');
        open();

        component.confirm();
        error$.next('');
        expect(errorSpy).not.toHaveBeenCalled();

        component.confirm();
        error$.next('It failed');
        expect(errorSpy).toHaveBeenCalledExactlyOnceWith('It failed');
    });

    it('should not report anything when it is cancelled', () => {
        const confirmedSpy = vi.fn();
        component.confirmed.subscribe(confirmedSpy);
        open();

        component.clear();

        expect(component.visible()).toBe(false);
        expect(confirmedSpy).not.toHaveBeenCalled();
    });
});
