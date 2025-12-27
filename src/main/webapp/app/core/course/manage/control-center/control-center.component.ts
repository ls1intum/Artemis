import { Component, input } from '@angular/core';
import { IrisEnabledComponent } from 'app/iris/manage/settings/shared/iris-enabled/iris-enabled.component';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';

@Component({
    selector: 'jhi-control-center',
    imports: [IrisEnabledComponent, TranslateDirective, HelpIconComponent, IrisLogoComponent],
    templateUrl: './control-center.component.html',
})
export class ControlCenterComponent {
    protected readonly IrisLogoSize = IrisLogoSize;
    course = input.required<Course>();
    irisEnabled = input.required<boolean>();
}
