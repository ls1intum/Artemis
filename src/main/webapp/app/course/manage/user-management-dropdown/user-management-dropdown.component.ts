import { Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IconDefinition, faGraduationCap, faListAlt, faPersonChalkboard, faSchool, faUser } from '@fortawesome/free-solid-svg-icons';
import { TumUiButtonDirective, TumUiMenuComponent, TumUiMenuItemDirective, TumUiMenuTriggerDirective } from '@tumaet/ui-angular';
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
    imports: [TumUiButtonDirective, TumUiMenuComponent, TumUiMenuItemDirective, TumUiMenuTriggerDirective, ArtemisTranslatePipe, TranslateDirective, RouterLink, FaIconComponent],
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
