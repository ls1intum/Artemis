import { Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IconDefinition, faClipboard, faGraduationCap, faListAlt, faPersonChalkboard, faQuestion, faSchool, faUser } from '@fortawesome/free-solid-svg-icons';
import { NgbDropdown, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle } from '@ng-bootstrap/ng-bootstrap';
import { ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';

export interface UserAddAction {
    icon: IconDefinition;
    routerLink: string | string[];
    label: string;
    id: string;
}

@Component({
    selector: 'jhi-user-management-dropdown',
    templateUrl: './user-management-dropdown.component.html',
    imports: [NgbDropdown, NgbDropdownItem, NgbDropdownToggle, NgbDropdownMenu, ArtemisTranslatePipe, TranslateDirective, RouterLink, FaIconComponent],
    styles: [
        `
            .user-mgmt-btn {
                display: inline-flex;
                align-items: center;
                gap: 0.5rem;
                padding: 0.5rem 0.875rem;
                border: 1px solid var(--bs-border-color);
                border-radius: 0.625rem;
                background: var(--overview-card-nested-bg, var(--bs-body-bg));
                color: var(--bs-body-color);
                font-weight: 500;
                font-size: 0.85rem;
                cursor: pointer;
                white-space: nowrap;
                transition: all 0.2s ease;

                &:hover {
                    background: var(--bs-tertiary-bg);
                    border-color: var(--bs-secondary-border-subtle);
                    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
                }

                &::after {
                    margin-left: 0.25rem;
                }

                fa-icon {
                    color: var(--bs-secondary-color);
                    font-size: 0.9rem;
                }
            }
        `,
    ],
})
export class UserManagementDropdownComponent {
    protected readonly ButtonType = ButtonType;
    protected readonly ButtonSize = ButtonSize;
    protected readonly faClipboard = faClipboard;
    protected readonly faGraduationCap = faGraduationCap;
    protected readonly faQuestion = faQuestion;
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
                routerLink: [`/course-management/${id}/groups/students`],
                label: 'entity.action.addStudent',
                id: 'add-student',
            },
            {
                icon: faPersonChalkboard,
                routerLink: [`/course-management/${id}/groups/tutors`],
                label: 'entity.action.addTutor',
                id: 'add-tutor',
            },
            {
                icon: faListAlt,
                routerLink: [`/course-management/${id}/groups/editors`],
                label: 'entity.action.addEditor',
                id: 'add-editor',
            },
            {
                icon: faGraduationCap,
                routerLink: [`/course-management/${id}/groups/instructors`],
                label: 'entity.action.addInstructor',
                id: 'add-instructor',
            },
        ];
    });
}
