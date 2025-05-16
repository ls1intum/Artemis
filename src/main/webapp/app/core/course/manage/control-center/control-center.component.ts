import { Component, input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ToggleButton } from 'primeng/togglebutton';
import { IrisEnabledComponent } from 'app/iris/manage/settings/shared/iris-enabled/iris-enabled.component';
import { Course } from 'app/core/course/shared/entities/course.model';
import { IrisSubSettingsType } from 'app/iris/shared/entities/settings/iris-sub-settings.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { Button } from 'primeng/button';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { faRobot } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-control-center',
    imports: [ToggleButton, FormsModule, IrisEnabledComponent, TranslateDirective, Button, HelpIconComponent, FaIconComponent],
    templateUrl: './control-center.component.html',
    styleUrl: './control-center.component.scss',
})
export class ControlCenterComponent {
    protected readonly IrisSubSettingsType = IrisSubSettingsType;
    protected readonly faRobot = faRobot;
    course = input.required<Course>();
    irisEnabled = input.required<boolean>();
}
