import { ChangeDetectorRef, Directive, inject } from '@angular/core';

@Directive()
export abstract class ExamPageComponent {
    protected changeDetectorReference = inject(ChangeDetectorRef);

    /**
     * Should be called when the component becomes active / visible. It activates Angular's change detection for this component.
     * We disabled Angular's change detection for invisible components, because of performance reasons. Here it is activated again,
     * that means once the component becomes active / visible, Angular will check for changes in the application state and update
     * the view if necessary. The performance improvement comes from not checking the components for updates while being invisible
     * For further customisation, individual submission components can override.
     */
    onActivate(): void {
        this.changeDetectorReference.reattach();
    }

    /**
     * Should be called when the component becomes deactivated / not visible. It deactivates Angular's change detection.
     * Angular change detection is responsible for synchronizing the view with the application state (often done over bindings)
     * We disabled Angular's change detection for invisible components for performance reasons. That means, that the component
     * will not be checked for updates and also will not be updated when it is invisible. Note: This works recursively, that means
     * subcomponents will also not be able to check for updates and update the view according to the application state.
     * For further customisation, individual submission components can override.
     */
    onDeactivate(): void {
        this.changeDetectorReference.detach();
    }
}
