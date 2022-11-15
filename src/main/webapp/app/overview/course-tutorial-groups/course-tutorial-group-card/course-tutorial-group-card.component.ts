import { Component, HostListener, Input } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { faPersonChalkboard } from '@fortawesome/free-solid-svg-icons';
import { Router } from '@angular/router';

@Component({
    selector: 'jhi-course-tutorial-group-card',
    templateUrl: './course-tutorial-group-card.component.html',
    styleUrls: ['./course-tutorial-group-card.component.scss'],
    host: { class: 'card tutorial-group-card' },
})
export class CourseTutorialGroupCardComponent {
    @Input()
    courseId: number;
    @Input()
    tutorialGroup: TutorialGroup;

    // icons
    faPersonChalkboard = faPersonChalkboard;

    @HostListener('click', ['$event'])
    onCardClicked() {
        this.router.navigate(['/courses', this.courseId, 'tutorial-groups', this.tutorialGroup.id]);
    }

    constructor(private router: Router) {}
}
