import { Component, inject, input, signal } from '@angular/core';
import { IrisEnabledComponent } from 'app/iris/manage/settings/shared/iris-enabled/iris-enabled.component';
import { Course } from 'app/course/shared/entities/course.model';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { AboutIrisModalComponent } from 'app/iris/overview/about-iris-modal/about-iris-modal.component';
import { AthenaEnabledComponent } from 'app/course/manage/control-center/athena-enabled/athena-enabled.component';
import { AthenaLogoComponent } from 'app/shared-ui/athena-logo/athena-logo.component';
import { AboutAthenaModalComponent } from 'app/course/manage/control-center/about-athena-modal/about-athena-modal.component';

@Component({
    selector: 'jhi-control-center',
    imports: [IrisEnabledComponent, IrisLogoComponent, AthenaEnabledComponent, AthenaLogoComponent, TranslateDirective, AboutAthenaModalComponent],
    templateUrl: './control-center.component.html',
    styleUrls: ['./control-center.component.scss'],
})
export class ControlCenterComponent {
    private dialogService = inject(DialogService);

    protected readonly IrisLogoSize = IrisLogoSize;
    private aboutIrisDialogRef: DynamicDialogRef<AboutIrisModalComponent> | undefined;

    course = input.required<Course>();
    irisEnabled = input.required<boolean>();
    athenaEnabled = input.required<boolean>();

    protected readonly aboutAthenaModalVisible = signal(false);

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

    openAboutAthenaModal(): void {
        this.aboutAthenaModalVisible.set(true);
    }
}
