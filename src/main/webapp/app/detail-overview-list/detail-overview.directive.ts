import { ComponentRef, Directive, Input, OnDestroy, OnInit, ViewContainerRef } from '@angular/core';
import { Detail, ShownDetail } from 'app/detail-overview-list/detail.model';
import { DetailType } from 'app/detail-overview-list/detail-overview-list.component';
import { TextDetailComponent } from 'app/detail-overview-list/components/text-detail.component';

@Directive({
    selector: '[jhiExerciseDetail]',
    standalone: true,
})
export class SidebarCardDirective implements OnInit, OnDestroy {
    @Input() detail: Detail;

    private componentRef: ComponentRef<any>;

    constructor(public viewContainerRef: ViewContainerRef) {}

    ngOnInit() {
        if (!this.isShownDetail()) {
            return;
        }
        this.detail = this.detail as ShownDetail;

        const detailTypeToComponent = {
            [DetailType.Text]: TextDetailComponent,
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
    private isShownDetail() {
        return !!this.detail;
    }

    private assignAttributes() {
        if (this.componentRef) {
            this.componentRef.instance.detail = this.detail;
        }
    }
}
