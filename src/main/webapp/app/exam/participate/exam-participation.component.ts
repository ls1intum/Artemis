import { Component, OnInit } from '@angular/core';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ActivatedRoute } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';

@Component({
    selector: 'jhi-exam-participation',
    templateUrl: './exam-participation.component.html',
    styleUrls: ['./exam-participation.scss'],
})
export class ExamParticipationComponent implements OnInit {
    course: Course;
    courseId = 0;
    unsavedChanges = false;
    disconnected = false;

    /**
     * Websocket channels
     */
    onConnected: () => void;
    onDisconnected: () => void;

    constructor(private courseService: CourseManagementService, private jhiWebsocketService: JhiWebsocketService, private route: ActivatedRoute) {}

    /**
     * initializes courseId and course
     */
    ngOnInit(): void {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.courseService.find(this.courseId).subscribe((courseResponse) => (this.course = courseResponse.body!));
        this.initLiveMode();
    }

    initLiveMode() {
        // listen to connect / disconnect events
        this.onConnected = () => {
            if (this.disconnected) {
                // if the disconnect happened during the live exam and there are unsaved changes, we trigger a selection changed event to save the submission on the server
                if (this.unsavedChanges) {
                    // ToDo: save submission on server
                }
            }
            this.disconnected = false;
        };
        this.jhiWebsocketService.bind('connect', () => {
            this.onConnected();
        });
        this.onDisconnected = () => {
            this.disconnected = true;
        };
        this.jhiWebsocketService.bind('disconnect', () => {
            this.onDisconnected();
        });
    }
}
