import { Component, input } from '@angular/core';
import { IrisEnabledComponent } from 'app/iris/manage/settings/shared/iris-enabled/iris-enabled.component';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { faRobot } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { CardWrapperComponent } from 'app/shared/card-wrapper/card-wrapper.component';

@Component({
    selector: 'jhi-control-center',
    imports: [IrisEnabledComponent, TranslateDirective, HelpIconComponent, FaIconComponent, CardWrapperComponent],
    templateUrl: './control-center.component.html',
})
export class ControlCenterComponent {
    protected readonly faRobot = faRobot;
    course = input.required<Course>();
    irisEnabled = input.required<boolean>();
}
