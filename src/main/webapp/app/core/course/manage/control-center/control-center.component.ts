import { Component, input } from '@angular/core';
import { IrisEnabledComponent } from 'app/iris/manage/settings/shared/iris-enabled/iris-enabled.component';
import { Course } from 'app/core/course/shared/entities/course.model';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-control-center',
    imports: [IrisEnabledComponent, HelpIconComponent, IrisLogoComponent, TranslateDirective],
    templateUrl: './control-center.component.html',
    styles: [
        `
            :host {
                display: block;
                flex: 0 0 auto;
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

            .iris-toggle ::ng-deep > div {
                width: 100%;
            }
        `,
    ],
})
export class ControlCenterComponent {
    protected readonly IrisLogoSize = IrisLogoSize;
    course = input.required<Course>();
    irisEnabled = input.required<boolean>();
}
