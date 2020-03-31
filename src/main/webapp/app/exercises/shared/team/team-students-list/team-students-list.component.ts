import { Component, Input } from '@angular/core';
import { User } from 'app/core/user/user.model';

@Component({
    selector: 'jhi-team-students-list',
    templateUrl: './team-students-list.component.html',
    styleUrls: ['./team-students-list.component.scss'],
})
export class TeamStudentsListComponent {
    @Input() students: User[];
    @Input() renderLinks = false;
}
