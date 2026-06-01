import { ComponentRef, Directive, OnDestroy, OnInit, Type, ViewContainerRef, inject, input } from '@angular/core';
import { Detail, ShownDetail } from 'app/shared-ui/detail-overview-list/detail.model';
import { DetailType } from 'app/shared-ui/detail-overview-list/detail-overview-list.component';
import { TextDetailComponent } from 'app/shared-ui/detail-overview-list/components/text-detail/text-detail.component';
import { DateDetailComponent } from 'app/shared-ui/detail-overview-list/components/date-detail/date-detail.component';
import { LinkDetailComponent } from 'app/shared-ui/detail-overview-list/components/link-detail/link-detail.component';
import { BooleanDetailComponent } from 'app/shared-ui/detail-overview-list/components/boolean-detail/boolean-detail.component';
import { ProgrammingRepositoryButtonsDetailComponent } from 'app/shared-ui/detail-overview-list/components/programming-repository-buttons-detail/programming-repository-buttons-detail.component';
import { ProgrammingAuxiliaryRepositoryButtonsDetailComponent } from 'app/shared-ui/detail-overview-list/components/programming-auxiliary-repository-buttons-detail/programming-auxiliary-repository-buttons-detail.component';
import { ProgrammingTestStatusDetailComponent } from 'app/shared-ui/detail-overview-list/components/programming-test-status-detail/programming-test-status-detail.component';
import { ProgrammingDiffReportDetailComponent } from 'app/shared-ui/detail-overview-list/components/programming-diff-report-detail/programming-diff-report-detail.component';
import { ExerciseCategoriesDetailComponent } from 'app/shared-ui/detail-overview-list/components/exercise-categories-detail/exercise-categories-detail.component';

@Directive({
    selector: '[jhiExerciseDetail]',
})
export class ExerciseDetailDirective implements OnInit, OnDestroy {
    viewContainerRef = inject(ViewContainerRef);

    detail = input<Detail>();

    private componentRef: ComponentRef<any>;

    ngOnInit() {
        const detail = this.detail();
        if (!this.isShownDetail(detail)) {
            return;
        }
        const shownDetail = detail as ShownDetail;

        const detailTypeToComponent: {
            [key in DetailType]?: Type<
                | TextDetailComponent
                | DateDetailComponent
                | LinkDetailComponent
                | BooleanDetailComponent
                | ProgrammingRepositoryButtonsDetailComponent
                | ProgrammingAuxiliaryRepositoryButtonsDetailComponent
                | ProgrammingTestStatusDetailComponent
                | ProgrammingDiffReportDetailComponent
                | ExerciseCategoriesDetailComponent
            >;
        } = {
            [DetailType.Text]: TextDetailComponent,
            [DetailType.Date]: DateDetailComponent,
            [DetailType.Link]: LinkDetailComponent,
            [DetailType.Boolean]: BooleanDetailComponent,
            [DetailType.ProgrammingRepositoryButtons]: ProgrammingRepositoryButtonsDetailComponent,
            [DetailType.ProgrammingAuxiliaryRepositoryButtons]: ProgrammingAuxiliaryRepositoryButtonsDetailComponent,
            [DetailType.ProgrammingTestStatus]: ProgrammingTestStatusDetailComponent,
            [DetailType.ProgrammingDiffReport]: ProgrammingDiffReportDetailComponent,
            [DetailType.ExerciseCategories]: ExerciseCategoriesDetailComponent,
        };

        const detailComponent = detailTypeToComponent[shownDetail.type];
        if (detailComponent) {
            this.componentRef = this.viewContainerRef.createComponent(detailComponent);
            this.assignAttributes(shownDetail);
        }
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
