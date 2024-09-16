import { Component, OnInit, input } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { CachingStrategy } from 'app/shared/image/secured-image.component';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';

@Component({
    selector: 'jhi-course-card-header',
    templateUrl: './course-card-header.component.html',
    styleUrls: ['./course-card-header.component.scss'],
})
export class CourseCardHeaderComponent implements OnInit {
    protected readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;
    course = input.required<Course>();
    archiveMode = input<boolean>(false);

    CachingStrategy = CachingStrategy;
    courseColor: string;

    ngOnInit() {
        this.courseColor = this.course().color || this.ARTEMIS_DEFAULT_COLOR;
    }
}
