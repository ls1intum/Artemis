import { Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { faGraduationCap } from '@fortawesome/free-solid-svg-icons/faGraduationCap';
import { faListAlt } from '@fortawesome/free-solid-svg-icons/faListAlt';
import { faPersonChalkboard } from '@fortawesome/free-solid-svg-icons/faPersonChalkboard';
import { faSchool } from '@fortawesome/free-solid-svg-icons/faSchool';
import { faUser } from '@fortawesome/free-solid-svg-icons/faUser';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular/button';
import { TumAetUiMenuComponent, TumAetUiMenuItemDirective, TumAetUiMenuTriggerDirective } from '@tumaet/ui-angular/menu';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

export interface UserAddAction {
    icon: IconDefinition;
    routerLink: string | string[];
    label: string;
    id: string;
}

@Component({
    selector: 'jhi-user-management-dropdown',
    templateUrl: './user-management-dropdown.component.html',
    imports: [
        TumAetUiButtonDirective,
        TumAetUiMenuComponent,
        TumAetUiMenuItemDirective,
        TumAetUiMenuTriggerDirective,
        ArtemisTranslatePipe,
        TranslateDirective,
        RouterLink,
        FaIconComponent,
    ],
})
export class UserManagementDropdownComponent {
    protected readonly faUser = faUser;
    readonly courseId = input<number | undefined>();

    readonly userAddActions = computed<UserAddAction[]>(() => {
        const id = this.courseId();
        if (!id) {
            return [];
        }
        return [
            {
                icon: faSchool,
                routerLink: [`/course-management/${id}/members/students`],
                label: 'entity.action.addStudent',
                id: 'add-student',
            },
            {
                icon: faPersonChalkboard,
                routerLink: [`/course-management/${id}/members/tutors`],
                label: 'entity.action.addTutor',
                id: 'add-tutor',
            },
            {
                icon: faListAlt,
                routerLink: [`/course-management/${id}/members/editors`],
                label: 'entity.action.addEditor',
                id: 'add-editor',
            },
            {
                icon: faGraduationCap,
                routerLink: [`/course-management/${id}/members/instructors`],
                label: 'entity.action.addInstructor',
                id: 'add-instructor',
            },
        ];
    });
}
