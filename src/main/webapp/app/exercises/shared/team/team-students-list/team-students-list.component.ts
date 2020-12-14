import { Component, Input } from '@angular/core';
import { User } from 'app/core/user/user.model';

@Component({
    selector: 'jhi-team-students-list',
    templateUrl: './team-students-list.component.html',
    styleUrls: ['./team-students-list.component.scss'],
})
export class TeamStudentsListComponent {
    @Input() students: User[];
    @Input() errorStudentLogins: string[] = [];
    @Input() renderLinks = false;
    @Input() withRegistrationNumber = false;
    @Input() errorStudentRegistrationNumbers: string[] = [];

    hasError(student: User) {
        return (
            (student.login && this.errorStudentLogins.includes(student.login)) ||
            (this.withRegistrationNumber && student.visibleRegistrationNumber && this.errorStudentRegistrationNumbers.includes(student.visibleRegistrationNumber))
        );
    }
}
