import { Component, input } from '@angular/core';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { InputTextModule } from 'primeng/inputtext';
import { ButtonModule } from 'primeng/button';
import { TutorialGroupRegisteredStudentDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { addPublicFilePrefix } from 'app/app.constants';

@Component({
    selector: 'jhi-tutorial-registrations',
    imports: [IconFieldModule, InputIconModule, InputTextModule, ButtonModule, ProfilePictureComponent],
    templateUrl: './tutorial-registrations.component.html',
    styleUrl: './tutorial-registrations.component.scss',
})
export class TutorialRegistrationsComponent {
    courseId = input.required<number>();
    tutorialGroupId = input.required<number>();
    registeredStudents = input.required<TutorialGroupRegisteredStudentDTO[]>();
    protected readonly addPublicFilePrefix = addPublicFilePrefix;
}
