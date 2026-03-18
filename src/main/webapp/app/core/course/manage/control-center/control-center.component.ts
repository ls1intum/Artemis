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
    styles: [
        `
            :host {
                display: block;
                flex: 0 0 auto;
                width: 100%;
            }

            @media (min-width: 768px) {
                :host {
                    max-width: 320px;
                    flex-shrink: 0;
                }
            }

            .iris-panel {
                border: 1px solid var(--bs-border-color);
                border-radius: 0.75rem;
                padding: 1.25rem;
                height: 100%;
                display: flex;
                flex-direction: column;
            }

            .iris-header {
                display: flex;
                align-items: center;
                justify-content: space-between;
                margin-bottom: 1rem;
            }

            .iris-title {
                display: flex;
                align-items: center;
                gap: 0.5rem;
                font-weight: 600;
                font-size: 1.1rem;
            }

            .iris-toggle {
                flex: 1;
                display: flex;
                align-items: center;
            }

            .iris-learn-more {
                font-size: 0.8rem;
                font-weight: 400;
                cursor: pointer;
                color: var(--bs-primary);
                text-decoration: none;

                &:hover {
                    text-decoration: underline;
                }
            }
        `,
    ],
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
