import { vi } from 'vitest';
import { ApplicationRef, Component, inject } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Logger, compile } from 'sass';
import { PrimeNG } from 'primeng/config';
import { TumAetUiDialogService } from '@tumaet/ui-angular';
import { TranslateService } from '@ngx-translate/core';
import { DeleteDialogService } from 'app/shared-ui/delete-dialog/service/delete-dialog.service';
import { DeleteDialogComponent } from 'app/shared-ui/delete-dialog/component/delete-dialog.component';
import { ActionType, DeleteDialogData } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

const THEME_STYLES = 'src/main/webapp/content/scss/themes/theme-default.scss';
const BOOTSTRAP_MODAL_Z_INDEX = 1055;
const ALERT_OVERLAY_Z_INDEX = 100000;
const deleteData: DeleteDialogData = {
    entityTitle: 'presentation',
    deleteQuestion: 'artemisApp.presentationAssessment.delete.question',
    translateValues: {},
    actionType: ActionType.Delete,
    buttonType: ButtonType.ERROR,
    delete: vi.fn<(additionalChecksValues: Record<string, boolean>) => void>(),
    requireConfirmationOnlyForAdditionalChecks: false,
};

@Component({ template: '<input aria-label="Presentation title" value="Unsaved title" /><button type="button" (click)="openConfirmation()">Delete presentation</button>' })
class PresentationDialogComponent {
    private readonly deleteDialogService = inject(DeleteDialogService);

    openConfirmation(): void {
        this.deleteDialogService.openDeleteDialog(deleteData);
    }
}

/** The compiled application CSS owns the shared overlay container's relationship to legacy dialogs and alerts. */
function compileOverlayContainerZIndex(): number {
    const { css } = compile(THEME_STYLES, {
        loadPaths: [process.cwd()],
        logger: Logger.silent,
        silenceDeprecations: ['color-functions', 'global-builtin', 'import', 'if-function'],
    });
    const rule = /(?:^|\n)\.cdk-overlay-container\s*\{([^}]*)\}/.exec(css);
    const zIndex = rule ? /z-index:\s*(\d+)/.exec(rule[1]) : null;
    if (!zIndex) {
        throw new Error('the compiled theme declares no z-index for .cdk-overlay-container');
    }
    return Number(zIndex[1]);
}

describe('DeleteDialogService layering', () => {
    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [PresentationDialogComponent, DeleteDialogComponent],
            providers: [
                DeleteDialogService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AlertService, useValue: { closeAll: vi.fn(), error: vi.fn() } },
            ],
        }).compileComponents();
    });

    afterEach(() => TestBed.inject(TumAetUiDialogService).closeAll());

    it('opens a confirmation after its parent in the shared overlay and restores the parent focus on cancellation', async () => {
        const dialogs = TestBed.inject(TumAetUiDialogService);
        dialogs.open(PresentationDialogComponent, { header: 'Edit presentation' });
        await TestBed.inject(ApplicationRef).whenStable();
        const parent = document.querySelector('[role="dialog"]')!;
        const title = parent.querySelector<HTMLInputElement>('[aria-label="Presentation title"]')!;
        const trigger = Array.from(parent.querySelectorAll('button')).find((button) => button.textContent?.includes('Delete presentation'))!;
        trigger.focus();
        trigger.click();
        await TestBed.inject(ApplicationRef).whenStable();

        const layers = document.querySelectorAll('[role="dialog"]');
        expect(layers).toHaveLength(2);
        expect(layers[0]).toBe(parent);
        expect(layers[1].textContent).toContain('entity.delete.title');
        const cancel = Array.from(layers[1].querySelectorAll('button')).find((button) => button.textContent?.includes('entity.action.cancel'))!;
        cancel.click();
        await TestBed.inject(ApplicationRef).whenStable();

        expect(document.querySelectorAll('[role="dialog"]')).toHaveLength(1);
        expect(document.activeElement).toBe(trigger);
        expect(document.querySelector('[aria-label="Presentation title"]')).toBe(title);
        expect(title.value).toBe('Unsaved title');
        expect(deleteData.delete).not.toHaveBeenCalled();
    });

    it('keeps the shared dialog and calendar container above legacy dialogs', () => {
        const containerZIndex = compileOverlayContainerZIndex();
        expect(containerZIndex).toBeGreaterThan(TestBed.inject(PrimeNG).zIndex.modal + 2);
        expect(containerZIndex).toBeGreaterThan(BOOTSTRAP_MODAL_Z_INDEX);
    });

    it('keeps confirmations below the alert overlay so a failed operation remains readable', () => {
        expect(compileOverlayContainerZIndex()).toBeLessThan(ALERT_OVERLAY_Z_INDEX);
    });
});
