import { Component, input } from '@angular/core';
import { User } from 'app/account/user/user.model';
import { RouterLink } from '@angular/router';

@Component({
    selector: 'jhi-team-students-list',
    templateUrl: './team-students-list.component.html',
    styleUrls: ['./team-students-list.component.scss'],
    imports: [RouterLink],
})
export class TeamStudentsListComponent {
    readonly students = input<User[]>(undefined!);
    readonly errorStudentLogins = input<string[]>([]);
    readonly renderLinks = input(false);
    readonly withRegistrationNumber = input(false);
    readonly errorStudentRegistrationNumbers = input<string[]>([]);

    hasError(student: User) {
        return (
            (student.login && this.errorStudentLogins().includes(student.login)) ||
            (this.withRegistrationNumber() && student.visibleRegistrationNumber && this.errorStudentRegistrationNumbers().includes(student.visibleRegistrationNumber))
        );
    }
}
