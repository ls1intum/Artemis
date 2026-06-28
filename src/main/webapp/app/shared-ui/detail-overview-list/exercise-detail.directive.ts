import { ComponentRef, DestroyRef, Directive, OnDestroy, OnInit, Type, ViewContainerRef, inject, input } from '@angular/core';
import { Detail, ShownDetail } from 'app/shared-ui/detail-overview-list/detail.model';
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
    private readonly viewContainerRef = inject(ViewContainerRef);
    private readonly destroyRef = inject(DestroyRef);

    readonly detail = input<Detail>();

    private componentRef: ComponentRef<any> | undefined;

    async ngOnInit() {
        const detail = this.detail();
        if (!this.isShownDetail(detail)) {
            return;
        }
        const shownDetail = detail as ShownDetail;

        // Light components — statically imported, negligible bundle cost.
        const eagerMap: Partial<Record<DetailType, Type<any>>> = {
            [DetailType.Text]: TextDetailComponent,
            [DetailType.Date]: DateDetailComponent,
            [DetailType.Link]: LinkDetailComponent,
            [DetailType.Boolean]: BooleanDetailComponent,
            [DetailType.ExerciseCategories]: ExerciseCategoriesDetailComponent,
        };

        // Heavy programming components — dynamically imported only when this
        // specific detail type is present in the rendered list.
        const lazyLoaders: Partial<Record<DetailType, () => Promise<Type<any>>>> = {
            [DetailType.ProgrammingRepositoryButtons]: () =>
                import('app/shared-ui/detail-overview-list/components/programming-repository-buttons-detail/programming-repository-buttons-detail.component').then(
                    (m) => m.ProgrammingRepositoryButtonsDetailComponent,
                ),
            [DetailType.ProgrammingAuxiliaryRepositoryButtons]: () =>
                import('app/shared-ui/detail-overview-list/components/programming-auxiliary-repository-buttons-detail/programming-auxiliary-repository-buttons-detail.component').then(
                    (m) => m.ProgrammingAuxiliaryRepositoryButtonsDetailComponent,
                ),
            [DetailType.ProgrammingTestStatus]: () =>
                import('app/shared-ui/detail-overview-list/components/programming-test-status-detail/programming-test-status-detail.component').then(
                    (m) => m.ProgrammingTestStatusDetailComponent,
                ),
            [DetailType.ProgrammingDiffReport]: () =>
                import('app/shared-ui/detail-overview-list/components/programming-diff-report-detail/programming-diff-report-detail.component').then(
                    (m) => m.ProgrammingDiffReportDetailComponent,
                ),
        };

        let detailComponent: Type<any> | undefined = eagerMap[shownDetail.type];

        if (!detailComponent) {
            const loader = lazyLoaders[shownDetail.type];
            if (loader) {
                detailComponent = await loader();
            }
        }

        // Guard: directive may have been destroyed while the dynamic import was
        // in flight (e.g. fast navigation away). Skip creation if so.
        let destroyed = false;
        this.destroyRef.onDestroy(() => (destroyed = true));
        if (destroyed || !detailComponent) {
            return;
        }

        this.componentRef = this.viewContainerRef.createComponent(detailComponent);
        this.assignAttributes(shownDetail);
    }

    ngOnDestroy() {
        this.componentRef?.destroy();
    }

    /**
     * @return false if the detail is a {@link NotShownDetail}
     */
    private isShownDetail(detail: Detail | undefined): boolean {
        return !!detail;
    }

    private assignAttributes(detail: ShownDetail) {
        if (this.componentRef) {
            this.componentRef.setInput('detail', detail);
        }
    }
}
