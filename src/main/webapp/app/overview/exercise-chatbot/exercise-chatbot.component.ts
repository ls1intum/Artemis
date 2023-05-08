import { Component, HostListener, Input, OnChanges } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';
import { CachingStrategy } from 'app/shared/image/secured-image.component';
import { faCommentDots } from '@fortawesome/free-solid-svg-icons';
import { MatDialog } from '@angular/material/dialog';
import { ChatbotPopupComponent } from './chatbot-popup/chatbot-popup.component';

@Component({
    selector: 'jhi-exercise-chatbot',
    templateUrl: './exercise-chatbot.component.html',
    styleUrls: ['./exercise-chatbot.component.scss'],
})
export class ExerciseChatbotComponent implements OnChanges {
    readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;
    readonly CachingStrategy = CachingStrategy;

    @Input() public course: Course;

    public courseDescription?: string;
    public enableShowMore = false;
    public longDescriptionShown = false;
    public chatAccepted = 'false';

    // Icons
    faCommentDots = faCommentDots;

    constructor(private dialog: MatDialog) {}

    openDialog() {
        const dialogRef = this.dialog.open(ChatbotPopupComponent, {
            data: {
                name: 'Samuel',
            },
        });

        dialogRef.afterClosed().subscribe((result) => {
            console.log(`${result}`);
            this.chatAccepted = result;
        });
    }

    openChat() {}

    ngOnChanges() {
        this.adjustCourseDescription();
    }

    @HostListener('window:resize')
    onResize() {
        this.adjustCourseDescription();
    }

    /**
     * Toggle between showing the long and abbreviated course description
     */
    toggleCourseDescription() {
        this.longDescriptionShown = !this.longDescriptionShown;
        this.adjustCourseDescription();
    }

    /**
     * Adjusts the course description and shows toggle buttons (if it is too long) based on the current window width
     */
    adjustCourseDescription() {
        const shortDescriptionLength = window.innerWidth / 5;
        if (this.course && this.course.description) {
            this.enableShowMore = this.course.description.length > shortDescriptionLength;
            if (this.enableShowMore && !this.longDescriptionShown) {
                this.courseDescription = this.course.description.slice(0, shortDescriptionLength) + '…';
            } else {
                this.courseDescription = this.course.description;
            }
        }
    }
}
