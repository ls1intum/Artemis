import { ComponentRef, DestroyRef, Directive, InputSignal, OnDestroy, OnInit, Type, ViewContainerRef, inject, input } from '@angular/core';
import { Detail, ShownDetail } from 'app/shared-ui/detail-overview-list/detail.model';
import { setInputs } from 'app/foundation/util/set-inputs.util';
import { DetailType } from 'app/shared-ui/detail-overview-list/detail-overview-list.component';
import { TextDetailComponent } from 'app/shared-ui/detail-overview-list/components/text-detail/text-detail.component';
import { DateDetailComponent } from 'app/shared-ui/detail-overview-list/components/date-detail/date-detail.component';
import { LinkDetailComponent } from 'app/shared-ui/detail-overview-list/components/link-detail/link-detail.component';
import { BooleanDetailComponent } from 'app/shared-ui/detail-overview-list/components/boolean-detail/boolean-detail.component';
import { ExerciseCategoriesDetailComponent } from 'app/shared-ui/detail-overview-list/components/exercise-categories-detail/exercise-categories-detail.component';
// The four programming-specific detail components are NOT imported statically.
// They are loaded via dynamic import() only when a detail row of that type is
// actually present. This keeps non-programming pages (course, quiz, text, …)
// free of the GitDiffReport / TriggerBuildButton / ResultComponent chain.

@Directive({
    selector: '[jhiExerciseDetail]',
})
export class ExerciseDetailDirective implements OnInit, OnDestroy {
    viewContainerRef = inject(ViewContainerRef);
    private readonly destroyRef = inject(DestroyRef);

    readonly detail = input<Detail>();

    private componentRef: ComponentRef<unknown> | undefined;

    ngOnInit() {
        void this.initializeExerciseDetailDirective();
    }

    /**
     * Picks the component for the type of the detail and renders it.
     *
     * The components are chosen with a `switch` on the type, not through a lookup table, for two reasons. The import to run
     * is always statically determined, not retrieved from data. And `switch` narrows the detail to the type that the chosen
     * component accepts, so {@link render} can check that the two belong together.
     */
    private async initializeExerciseDetailDirective(): Promise<void> {
        const shownDetail = this.detail();
        if (!this.isShownDetail(shownDetail)) {
            return;
        }

        // Guard: directive may be destroyed while the dynamic import below is
        // in flight (e.g. fast navigation away). Must be registered before the
        // await, since DestroyRef.onDestroy() throws if called after the view
        // has already been destroyed.
        let destroyed = false;
        this.destroyRef.onDestroy(() => (destroyed = true));

        switch (shownDetail.type) {
            // Light components — statically imported, negligible bundle cost.
            case DetailType.Text:
                this.render(TextDetailComponent, shownDetail);
                break;
            case DetailType.Date:
                this.render(DateDetailComponent, shownDetail);
                break;
            case DetailType.Link:
                this.render(LinkDetailComponent, shownDetail);
                break;
            case DetailType.Boolean:
                this.render(BooleanDetailComponent, shownDetail);
                break;
            case DetailType.ExerciseCategories:
                this.render(ExerciseCategoriesDetailComponent, shownDetail);
                break;
            // Heavy programming components — dynamically imported only when this specific detail type is present.
            case DetailType.ProgrammingRepositoryButtons: {
                const component =
                    await import('app/shared-ui/detail-overview-list/components/programming-repository-buttons-detail/programming-repository-buttons-detail.component').then(
                        (m) => m.ProgrammingRepositoryButtonsDetailComponent,
                    );
                if (!destroyed) {
                    this.render(component, shownDetail);
                }
                break;
            }
            case DetailType.ProgrammingAuxiliaryRepositoryButtons: {
                const component =
                    await import('app/shared-ui/detail-overview-list/components/programming-auxiliary-repository-buttons-detail/programming-auxiliary-repository-buttons-detail.component').then(
                        (m) => m.ProgrammingAuxiliaryRepositoryButtonsDetailComponent,
                    );
                if (!destroyed) {
                    this.render(component, shownDetail);
                }
                break;
            }
            case DetailType.ProgrammingTestStatus: {
                const component = await import('app/shared-ui/detail-overview-list/components/programming-test-status-detail/programming-test-status-detail.component').then(
                    (m) => m.ProgrammingTestStatusDetailComponent,
                );
                if (!destroyed) {
                    this.render(component, shownDetail);
                }
                break;
            }
            case DetailType.ProgrammingDiffReport: {
                const component = await import('app/shared-ui/detail-overview-list/components/programming-diff-report-detail/programming-diff-report-detail.component').then(
                    (m) => m.ProgrammingDiffReportDetailComponent,
                );
                if (!destroyed) {
                    this.render(component, shownDetail);
                }
                break;
            }
            default:
                // The other detail types are rendered by the detail overview list itself.
                break;
        }
    }

    ngOnDestroy() {
        this.componentRef?.destroy();
    }

    /**
     * @return false if the detail is a {@link NotShownDetail}, narrowing to {@link ShownDetail} otherwise
     */
    private isShownDetail(detail: Detail | undefined): detail is ShownDetail {
        return !!detail;
    }

    /**
     * Creates the component and hands it the detail. The component and the detail share one type parameter, so a component
     * is only accepted together with the kind of detail it declares as its input.
     */
    private render<D extends ShownDetail>(component: Type<{ readonly detail: InputSignal<D> }>, detail: D): void {
        const componentRef = this.viewContainerRef.createComponent(component);
        setInputs(componentRef, { detail });
        this.componentRef = componentRef;
    }
}
