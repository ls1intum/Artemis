import { Component, OnInit, input } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { CachingStrategy } from 'app/shared/image/secured-image.component';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

/** TODO '@edkaya': New course card design also uses this header design.
 * Therefore, this component will be reused in course-card.component.html
 * after new course cards are merged into develop. I will refactor its html
 * and scss file to avoid duplicates to maintain reusability in the follow-up.
 * */
@Component({
    selector: 'jhi-course-card-header',
    templateUrl: './course-card-header.component.html',
    styleUrls: ['./course-card-header.component.scss'],
    standalone: true,
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule],
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
