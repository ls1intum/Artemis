import { Component, inject, input } from '@angular/core';
import { IrisEnabledComponent } from 'app/iris/manage/settings/shared/iris-enabled/iris-enabled.component';
import { Course } from 'app/core/course/shared/entities/course.model';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { AboutIrisModalComponent } from 'app/iris/overview/about-iris-modal/about-iris-modal.component';

@Component({
    selector: 'jhi-control-center',
    imports: [IrisEnabledComponent, IrisLogoComponent, TranslateDirective],
    templateUrl: './control-center.component.html',
    styleUrls: ['./control-center.component.scss'],
})
export class ControlCenterComponent {
    protected readonly IrisLogoSize = IrisLogoSize;
    private dialogService = inject(DialogService);
    private aboutIrisDialogRef: DynamicDialogRef<AboutIrisModalComponent> | undefined;

    course = input.required<Course>();
    irisEnabled = input.required<boolean>();

    openAboutIrisModal(): void {
        this.aboutIrisDialogRef?.close();
        this.aboutIrisDialogRef =
            this.dialogService.open(AboutIrisModalComponent, {
                modal: true,
                closable: false,
                dismissableMask: true,
                showHeader: false,
                styleClass: 'about-iris-dialog',
                maskStyleClass: 'about-iris-dialog',
                width: '40rem',
                breakpoints: { '640px': '95vw' },
                data: { hideTryButton: true },
            }) ?? undefined;
    }
}
