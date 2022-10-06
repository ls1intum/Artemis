import { Component, HostListener, Input, OnChanges, SimpleChanges } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { User } from 'app/core/user/user.model';
import { faPersonChalkboard } from '@fortawesome/free-solid-svg-icons';
import { Router } from '@angular/router';

@Component({
    selector: 'jhi-course-tutorial-group-card',
    templateUrl: './course-tutorial-group-card.component.html',
    styleUrls: ['./course-tutorial-group-card.component.scss'],
    host: { class: 'card tutorial-group-card' },
})
export class CourseTutorialGroupCardComponent implements OnChanges {
    @Input()
    courseId: number;
    @Input()
    tutorialGroup: TutorialGroup;
    // ToDo: Determine upcoming tutorial group sessions
    upcomingSession: undefined;

    // icons
    faPersonChalkboard = faPersonChalkboard;

    @HostListener('click', ['$event'])
    onCardClicked() {
        this.router.navigate(['/courses', this.courseId, 'tutorial-groups', this.tutorialGroup.id]);
    }

    constructor(private router: Router) {}

    ngOnChanges(changes: SimpleChanges) {
        for (const propName in changes) {
            if (changes.hasOwnProperty(propName)) {
                const change = changes[propName];
                switch (propName) {
                    case 'tutorialGroup': {
                        if (change.currentValue) {
                            this.tutorialGroup = change.currentValue;
                        }
                    }
                }
            }
        }
    }
}
