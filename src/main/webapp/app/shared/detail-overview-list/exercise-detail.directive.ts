import { ComponentRef, Directive, Input, OnDestroy, OnInit, Type, ViewContainerRef, inject } from '@angular/core';
import { Detail, ShownDetail } from 'app/shared/detail-overview-list/detail.model';
import { DetailType } from 'app/shared/detail-overview-list/detail-overview-list.component';
import { TextDetailComponent } from 'app/shared/detail-overview-list/components/text-detail/text-detail.component';
import { DateDetailComponent } from 'app/shared/detail-overview-list/components/date-detail/date-detail.component';
import { LinkDetailComponent } from 'app/shared/detail-overview-list/components/link-detail/link-detail.component';
import { BooleanDetailComponent } from 'app/shared/detail-overview-list/components/boolean-detail/boolean-detail.component';
import { ProgrammingRepositoryButtonsDetailComponent } from 'app/shared/detail-overview-list/components/programming-repository-buttons-detail/programming-repository-buttons-detail.component';
import { ProgrammingAuxiliaryRepositoryButtonsDetailComponent } from 'app/shared/detail-overview-list/components/programming-auxiliary-repository-buttons-detail/programming-auxiliary-repository-buttons-detail.component';
import { ProgrammingTestStatusDetailComponent } from 'app/shared/detail-overview-list/components/programming-test-status-detail/programming-test-status-detail.component';
import { ProgrammingDiffReportDetailComponent } from 'app/shared/detail-overview-list/components/programming-diff-report-detail/programming-diff-report-detail.component';
import { ExerciseCategoriesDetailComponent } from 'app/shared/detail-overview-list/components/exercise-categories-detail/exercise-categories-detail.component';

@Directive({
    selector: '[jhiExerciseDetail]',
})
export class ExerciseDetailDirective implements OnInit, OnDestroy {
    viewContainerRef = inject(ViewContainerRef);

    @Input() detail: Detail;

    private componentRef: ComponentRef<any>;

    ngOnInit() {
        if (!this.isShownDetail()) {
            return;
        }
        this.detail = this.detail as ShownDetail;

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

        const detailComponent = detailTypeToComponent[this.detail.type];
        if (detailComponent) {
            this.componentRef = this.viewContainerRef.createComponent(detailComponent);
            this.assignAttributes();
        }
    }

    ngOnDestroy() {
        this.componentRef?.destroy();
    }

    /**
     * @return false if the detail is a {@link NotShownDetail}
     */
    private isShownDetail(): boolean {
        return !!this.detail;
    }

    private assignAttributes() {
        if (this.componentRef) {
            this.componentRef.setInput('detail', this.detail);
        }
    }
}
