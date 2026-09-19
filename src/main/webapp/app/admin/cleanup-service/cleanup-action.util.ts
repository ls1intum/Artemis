import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { faRotateLeft, faTrash, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import { TumUiButtonSeverity } from '@tumaet/ui-angular';
import { CleanupAction } from 'app/admin/cleanup-service/cleanup-operation.model';

/** How the confirmation button of a cleanup action presents itself. */
export interface CleanupActionPresentation {
    icon: IconDefinition;
    labelKey: string;
    severity: TumUiButtonSeverity;
}

/**
 * The confirmation button per cleanup action, for the row and for the dialog. A trash can labelled "Delete" is wrong for
 * an operation that only sends a warning email, or that resets a course's student data while keeping the course itself.
 *
 * A warning destroys nothing on its own — it archives, emails, and starts a grace period the recipient can still act
 * within — so it must not carry the red of an irreversible deletion. A reset does destroy student data, so it keeps it.
 *
 * Exported as a record rather than as lookup functions because templates must not call methods (`methods_in_html`).
 */
export const CLEANUP_ACTION_PRESENTATION: Record<CleanupAction, CleanupActionPresentation> = {
    delete: { icon: faTrash, labelKey: 'entity.action.delete', severity: 'danger' },
    warn: { icon: faTriangleExclamation, labelKey: 'entity.action.warn', severity: 'warn' },
    reset: { icon: faRotateLeft, labelKey: 'entity.action.reset', severity: 'danger' },
};
