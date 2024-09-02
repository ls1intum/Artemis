import { Component, Input, OnInit } from '@angular/core';
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
    @Input() course: Course;
    @Input() courseColor: string;
    @Input() archiveMode = false;

    CachingStrategy = CachingStrategy;

    ngOnInit() {
        this.courseColor = this.course.color || this.ARTEMIS_DEFAULT_COLOR;
    }
}
