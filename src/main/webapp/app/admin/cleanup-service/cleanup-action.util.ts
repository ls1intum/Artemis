import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { faRotateLeft, faTrash, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import { TumUiButtonSeverity } from '@tumaet/ui-angular';
import { CleanupAction } from 'app/admin/cleanup-service/cleanup-operation.model';

const ICON_BY_ACTION: Record<CleanupAction, IconDefinition> = {
    delete: faTrash,
    warn: faTriangleExclamation,
    reset: faRotateLeft,
};

const LABEL_KEY_BY_ACTION: Record<CleanupAction, string> = {
    delete: 'entity.action.delete',
    warn: 'entity.action.warn',
    reset: 'entity.action.reset',
};

// A warning destroys nothing on its own — it archives, emails, and starts a grace period the recipient can still act
// within — so it must not carry the red of an irreversible deletion. A reset does destroy student data, so it keeps it.
const SEVERITY_BY_ACTION: Record<CleanupAction, TumUiButtonSeverity> = {
    delete: 'danger',
    warn: 'warn',
    reset: 'danger',
};

/**
 * The icon of the confirmation button for a cleanup action. A trash can is wrong for an operation that only sends a
 * warning email, or that resets a course's student data while keeping the course itself.
 *
 * @param action what the operation does to the affected entities
 * @return the icon to render on the button
 */
export function cleanupActionIcon(action: CleanupAction): IconDefinition {
    return ICON_BY_ACTION[action];
}

/**
 * The translation key of the confirmation button label for a cleanup action ("Delete", "Warn" or "Reset").
 *
 * @param action what the operation does to the affected entities
 * @return the translation key of the button label
 */
export function cleanupActionLabelKey(action: CleanupAction): string {
    return LABEL_KEY_BY_ACTION[action];
}

/**
 * The button severity for a cleanup action.
 *
 * @param action what the operation does to the affected entities
 * @return the TUM UI button severity
 */
export function cleanupActionSeverity(action: CleanupAction): TumUiButtonSeverity {
    return SEVERITY_BY_ACTION[action];
}
