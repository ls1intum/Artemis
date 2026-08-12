import { Component, inject, signal, viewChild } from '@angular/core';
import { PROFILE_LOCALCI } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ComponentCanDeactivate } from 'app/foundation/guard/can-deactivate.model';
import { BuildPlanEditorComponent } from 'app/programming/manage/build-plan-editor/build-plan-editor.component';
import { LocalCIBuildPlanEditorComponent } from 'app/programming/manage/build-plan-editor/localci-build-plan-editor.component';

/**
 * Routed wrapper for the "Edit build plan" page. It selects the appropriate editor for the active CI system: the
 * structured build plan editor for LocalCI, and the script-based editor for external CI systems (e.g. Jenkins). Both
 * editors read the resolved exercise from this route.
 */
@Component({
    selector: 'jhi-build-plan-editor-page',
    template: `
        @if (isLocalCIActive()) {
            <jhi-localci-build-plan-editor />
        } @else {
            <jhi-build-plan-editor />
        }
    `,
    imports: [BuildPlanEditorComponent, LocalCIBuildPlanEditorComponent],
})
export class BuildPlanEditorPageComponent implements ComponentCanDeactivate {
    private profileService = inject(ProfileService);

    readonly isLocalCIActive = signal(this.profileService.isProfileActive(PROFILE_LOCALCI));

    private readonly localCIEditor = viewChild(LocalCIBuildPlanEditorComponent);

    /**
     * Delegates the unsaved-changes check to the active LocalCI editor so the {@link PendingChangesGuard} warns before its
     * edits are discarded. The external CI editor keeps no editable state here, so leaving it is always allowed.
     */
    canDeactivate(): boolean {
        return this.localCIEditor()?.canDeactivate() ?? true;
    }
}
