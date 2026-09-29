import { Signal } from '@angular/core';
import { CanDeactivateFn } from '@angular/router';
import { Observable, of } from 'rxjs';

/**
 * Interface for components that can have unsaved lecture changes.
 * Extracted to allow testing without importing heavy component dependencies.
 */
export interface LectureUnsavedChangesComponent {
    shouldDisplayDismissWarning: boolean;
    hasUnsavedChanges: Signal<boolean>;
    /** Asks whether to discard the unsaved changes; emits true once when they may be discarded and false when the user keeps editing. */
    confirmDiscardChanges(): Observable<boolean>;
}

export const hasLectureUnsavedChangesGuard: CanDeactivateFn<LectureUnsavedChangesComponent> = (component: LectureUnsavedChangesComponent): Observable<boolean> => {
    if (!component.shouldDisplayDismissWarning || !component.hasUnsavedChanges()) {
        return of(true);
    }
    return component.confirmDiscardChanges();
};
