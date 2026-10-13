import { CanDeactivateFn } from '@angular/router';
import { Observable, of } from 'rxjs';

/**
 * Interface for components that can have unsaved lecture changes.
 * Extracted to allow testing without importing heavy component dependencies.
 */
export interface LectureUnsavedChangesComponent {
    shouldDisplayDismissWarning: boolean;
    /** Decides whether the page can be left, asking the user when changes would be lost; emits true once when it may be left and false otherwise. */
    confirmLeave(): Observable<boolean>;
}

export const hasLectureUnsavedChangesGuard: CanDeactivateFn<LectureUnsavedChangesComponent> = (component: LectureUnsavedChangesComponent): Observable<boolean> => {
    if (!component.shouldDisplayDismissWarning) {
        return of(true);
    }
    return component.confirmLeave();
};
