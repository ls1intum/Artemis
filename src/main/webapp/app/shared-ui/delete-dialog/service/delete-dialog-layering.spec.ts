import { vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { Logger, compile } from 'sass';
import { PrimeNG } from 'primeng/config';
import { DialogService } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { EventEmitter } from '@angular/core';
import { Subject } from 'rxjs';
import { CONFIRMATION_DIALOG_BASE_Z_INDEX, DeleteDialogService } from 'app/shared-ui/delete-dialog/service/delete-dialog.service';
import { ActionType, DeleteDialogData } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

/**
 * Layering contract between the CDK overlay container and the PrimeNG confirmation dialog.
 *
 * The TUM AET UI dialog (presentation edit dialog) and everything the kit opens (calendars, select panels) live in
 * `.cdk-overlay-container`; the delete confirmation is a PrimeNG dynamic dialog. The container has to stay above the first
 * PrimeNG dialog so a calendar inside a PrimeNG dialog is visible, and the confirmation has to stay above the container so it
 * is not hidden by the kit dialog that opened it. `theme-default.scss`, which pulls in `global.scss`, is compiled here so the check sees the shipped value instead of
 * a re-typed copy.
 */
const THEME_STYLES = 'src/main/webapp/content/scss/themes/theme-default.scss';

/** Bootstrap's `$zindex-modal`; an NgbModal opened from a kit dialog was already below the container before the date picker change. */
const BOOTSTRAP_MODAL_Z_INDEX = 1055;

/** Alerts must stay above every dialog so an error after a failed delete is readable. */
const ALERT_OVERLAY_Z_INDEX = 100000;

/** Reads the `z-index` declared for `.cdk-overlay-container` from the compiled global styles. */
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
    let service: DeleteDialogService;
    let dialogService: { open: ReturnType<typeof vi.fn> };
    let primeNgModalZIndex: number;

    const data: DeleteDialogData = {
        entityTitle: 'title',
        deleteQuestion: 'artemisApp.presentationAssessment.delete.question',
        translateValues: {},
        actionType: ActionType.Delete,
        buttonType: ButtonType.ERROR,
        delete: new EventEmitter<any>(),
        requireConfirmationOnlyForAdditionalChecks: false,
    };

    beforeEach(() => {
        dialogService = { open: vi.fn().mockReturnValue({ onClose: new Subject<void>(), close: vi.fn() }) };
        TestBed.configureTestingModule({
            providers: [
                DeleteDialogService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: DialogService, useValue: dialogService },
                { provide: AlertService, useValue: { closeAll: vi.fn(), error: vi.fn() } },
            ],
        });
        service = TestBed.inject(DeleteDialogService);
        primeNgModalZIndex = TestBed.inject(PrimeNG).zIndex.modal;
    });

    /** z-index PrimeNG gives the confirmation when no other PrimeNG layer is open (base + 2, see ZIndexUtils.generateZIndex). */
    function openConfirmationZIndex(): number {
        service.openDeleteDialog(data);
        const config = dialogService.open.mock.calls[0][1] as { baseZIndex?: number };
        return primeNgModalZIndex + (config.baseZIndex ?? 0) + 2;
    }

    it('opens the confirmation above the CDK overlay container that hosts the dialog it was opened from', () => {
        const containerZIndex = compileOverlayContainerZIndex();

        expect(openConfirmationZIndex()).toBeGreaterThan(containerZIndex);
    });

    it('keeps the CDK overlay container above the first PrimeNG dialog so a calendar inside it is visible', () => {
        const containerZIndex = compileOverlayContainerZIndex();

        expect(containerZIndex).toBeGreaterThan(primeNgModalZIndex + 2);
        expect(containerZIndex).toBeGreaterThan(BOOTSTRAP_MODAL_Z_INDEX);
    });

    it('keeps the confirmation below the alert overlay', () => {
        expect(openConfirmationZIndex()).toBeLessThan(ALERT_OVERLAY_Z_INDEX);
        expect(CONFIRMATION_DIALOG_BASE_Z_INDEX).toBeGreaterThan(0);
    });
});
