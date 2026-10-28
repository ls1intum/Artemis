import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiDialogComponent } from '@tumaet/ui-angular';
import { DeleteDialogService } from 'app/shared-ui/delete-dialog/service/delete-dialog.service';
import { ActionType } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

@Component({
    imports: [TumAetUiDialogComponent],
    template: '<tumaet-ui-dialog [(visible)]="visible" header="Edit lecture"><input aria-label="Lecture title" value="Unsaved title" /></tumaet-ui-dialog>',
})
class EditLectureHost {
    readonly visible = signal(true);
}

describe('Delete confirmation opened from another dialog', () => {
    it('keeps its caller open and preserves its unsaved input when dismissed', async () => {
        TestBed.configureTestingModule({
            imports: [EditLectureHost],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AlertService, useValue: { closeAll: vi.fn(), error: vi.fn() } },
            ],
        });
        const fixture = TestBed.createComponent(EditLectureHost);
        fixture.detectChanges();
        await fixture.whenStable();
        const title = document.querySelector<HTMLInputElement>('[aria-label="Lecture title"]')!;
        const service = TestBed.inject(DeleteDialogService);
        const deleted = vi.fn();
        service.openDeleteDialog({
            entityTitle: 'Lecture',
            translateValues: {},
            actionType: ActionType.Delete,
            buttonType: ButtonType.ERROR,
            delete: deleted,
            requireConfirmationOnlyForAdditionalChecks: false,
        });
        TestBed.tick();
        await fixture.whenStable();
        expect(document.querySelectorAll('[role="dialog"]')).toHaveLength(2);
        service.dialogRef()!.close();
        TestBed.tick();
        await fixture.whenStable();
        expect(document.querySelectorAll('[role="dialog"]')).toHaveLength(1);
        expect(document.querySelector('[aria-label="Lecture title"]')).toBe(title);
        expect(title.value).toBe('Unsaved title');
        expect(deleted).not.toHaveBeenCalled();
        fixture.destroy();
    });
});
