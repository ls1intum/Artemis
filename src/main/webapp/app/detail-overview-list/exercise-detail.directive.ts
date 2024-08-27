import { ComponentRef, Directive, Input, OnDestroy, OnInit, Type, ViewContainerRef } from '@angular/core';
import type { Detail, ShownDetail } from 'app/detail-overview-list/detail.model';
import { DetailType } from 'app/detail-overview-list/detail-overview-list.component';
import { TextDetailComponent } from 'app/detail-overview-list/components/text-detail.component';
import { DateDetailComponent } from 'app/detail-overview-list/components/date-detail.component';
import { LinkDetailComponent } from 'app/detail-overview-list/components/link-detail.component';
import { BooleanDetailComponent } from 'app/detail-overview-list/components/boolean-detail.component';
import { ProgrammingRepositoryButtonsDetailComponent } from 'app/detail-overview-list/components/programming-repository-buttons-detail.component';
import { ProgrammingAuxiliaryRepositoryButtonsDetailComponent } from 'app/detail-overview-list/components/programming-auxiliary-repository-buttons-detail.component';

@Directive({
    selector: '[jhiExerciseDetail]',
    standalone: true,
})
export class ExerciseDetailDirective implements OnInit, OnDestroy {
    @Input() detail: Detail;

    private componentRef: ComponentRef<any>;

    constructor(public viewContainerRef: ViewContainerRef) {}

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
            >;
        } = {
            [DetailType.Text]: TextDetailComponent,
            [DetailType.Date]: DateDetailComponent,
            [DetailType.Link]: LinkDetailComponent,
            [DetailType.Boolean]: BooleanDetailComponent,
            [DetailType.ProgrammingRepositoryButtons]: ProgrammingRepositoryButtonsDetailComponent,
            [DetailType.ProgrammingAuxiliaryRepositoryButtons]: ProgrammingAuxiliaryRepositoryButtonsDetailComponent,
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
            this.componentRef.instance.detail = this.detail;
        }
    }
}
