import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { TutorialGroupRegistration } from 'app/entities/tutorial-group/tutorial-group-registration.model';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { User } from 'app/core/user/user.model';
import { faPersonChalkboard } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-course-tutorial-group-card',
    templateUrl: './course-tutorial-group-card.component.html',
    styleUrls: ['./course-tutorial-group-card.component.scss'],
    host: { class: 'card tutorial-group-card' },
})
export class CourseTutorialGroupCardComponent implements OnChanges {
    @Input()
    tutorialGroupRegistration: TutorialGroupRegistration;
    tutorialGroup: TutorialGroup;
    teachingAssistant: User;
    // ToDo: Determine upcoming tutorial group sessions
    upcomingSession: undefined;

    // icons
    faPersonChalkboard = faPersonChalkboard;

    ngOnChanges(changes: SimpleChanges) {
        for (const propName in changes) {
            if (changes.hasOwnProperty(propName)) {
                const change = changes[propName];
                switch (propName) {
                    case 'tutorialGroupRegistration': {
                        if (change.currentValue && change.currentValue.tutorialGroup) {
                            this.tutorialGroup = change.currentValue.tutorialGroup;
                            if (this.tutorialGroup && this.tutorialGroup.teachingAssistant) {
                                this.teachingAssistant = this.tutorialGroup.teachingAssistant;
                            }
                        }
                    }
                }
            }
        }
    }
}
