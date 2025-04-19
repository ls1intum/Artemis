import { Component, OnInit, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IconDefinition, faClipboard, faGraduationCap, faListAlt, faPersonChalkboard, faQuestion, faSchool, faUser } from '@fortawesome/free-solid-svg-icons';
import { NgbDropdown, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle } from '@ng-bootstrap/ng-bootstrap';
import { ButtonSize, ButtonType } from 'app/shared/components/button/button.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';

export interface UserAddAction {
    icon: IconDefinition;
    routerLink: string | string[];
    translationKey: string;
}

@Component({
    selector: 'jhi-user-management-dropdown',
    templateUrl: './user-management-dropdown.component.html',
    imports: [NgbDropdown, NgbDropdownItem, NgbDropdownToggle, NgbDropdownMenu, ArtemisTranslatePipe, TranslateDirective, RouterLink, FaIconComponent],
    styleUrls: ['./user-management-dropdown.component.scss'],
})
export class UserManagementDropdownComponent implements OnInit {
    courseId = input<number | undefined>();
    userAddActions: UserAddAction[] = [];

    protected readonly ButtonType = ButtonType;
    protected readonly ButtonSize = ButtonSize;
    protected readonly faClipboard = faClipboard;
    protected readonly faGraduationCap = faGraduationCap;
    protected readonly faQuestion = faQuestion;
    protected readonly faUser = faUser;

    ngOnInit() {
        if (!this.courseId()) {
            return;
        }
        this.userAddActions = [
            {
                icon: faSchool,
                routerLink: [`/course-management/${this.courseId()}/groups/students`],
                translationKey: 'entity.action.addStudent',
            },
            {
                icon: faPersonChalkboard,
                routerLink: [`/course-management/${this.courseId()}/groups/tutors`],
                translationKey: 'entity.action.addTutor',
            },
            {
                icon: faListAlt,
                routerLink: [`/course-management/${this.courseId()}/groups/editors`],
                translationKey: 'entity.action.addEditor',
            },
            {
                icon: faGraduationCap,
                routerLink: [`/course-management/${this.courseId()}/groups/instructors`],
                translationKey: 'entity.action.addInstructor',
            },
        ];
    }
}
