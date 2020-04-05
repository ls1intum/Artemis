import { Component, Input, OnInit } from '@angular/core';
import { Team } from 'app/entities/team.model';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { orderBy } from 'lodash';

@Component({
    selector: 'jhi-team-students-online-list',
    templateUrl: './team-students-online-list.component.html',
    styleUrls: ['./team-students-online-list.component.scss'],
})
export class TeamStudentsOnlineListComponent implements OnInit {
    @Input() team: Team;

    currentUser: User;

    constructor(private accountService: AccountService) {}

    ngOnInit(): void {
        this.accountService.identity().then((user: User) => {
            this.currentUser = user;
        });
    }

    get studentList(): User[] {
        return [...(this.self ? [this.self] : []), ...orderBy(this.otherStudents, ['name'])];
    }

    get self(): User | undefined {
        return this.team.students.find(this.isSelf);
    }

    get otherStudents(): User[] {
        return this.team.students.filter(this.isOther);
    }

    isSelf = (user: User): boolean => {
        return user.id === this.currentUser?.id;
    };

    isOther = (user: User): boolean => {
        return !this.isSelf(user);
    };
}
