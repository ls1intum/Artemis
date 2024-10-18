import { Component, OnInit, input } from '@angular/core';
import { CachingStrategy } from 'app/shared/image/secured-image.component';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@Component({
    selector: 'jhi-course-card-header',
    templateUrl: './course-card-header.component.html',
    styleUrls: ['./course-card-header.component.scss'],
    standalone: true,
    imports: [ArtemisSharedModule],
})
export class CourseCardHeaderComponent implements OnInit {
    protected readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;
    courseIcon = input.required<string>();
    courseTitle = input.required<string>();
    courseColor = input.required<string>();
    courseId = input.required<number>();
    archiveMode = input<boolean>(false);

    CachingStrategy = CachingStrategy;
    color: string;

    ngOnInit() {
        this.color = this.courseColor() || this.ARTEMIS_DEFAULT_COLOR;
    }
}
