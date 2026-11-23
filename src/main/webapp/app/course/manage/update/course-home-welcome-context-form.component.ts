import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TumAetUiFormFieldComponent, TumAetUiInputDirective } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

/** Instructor-authored learning guidance shown on the student course Home. */
@Component({
    selector: 'jhi-course-home-welcome-context-form',
    imports: [ReactiveFormsModule, TumAetUiFormFieldComponent, TumAetUiInputDirective, ArtemisTranslatePipe],
    templateUrl: './course-home-welcome-context-form.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CourseHomeWelcomeContextFormComponent {
    readonly control = input.required<FormControl<string>>();
}
