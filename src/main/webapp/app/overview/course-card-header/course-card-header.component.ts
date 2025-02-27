import { Component, OnInit, input } from '@angular/core';
import { CachingStrategy, SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';
import { RouterModule } from '@angular/router';
import { CommonModule, SlicePipe } from '@angular/common';
import { getContrastingTextColor } from 'app/utils/color.utils';

@Component({
    selector: 'jhi-course-card-header',
    templateUrl: './course-card-header.component.html',
    styleUrls: ['./course-card-header.component.scss'],
    imports: [SecuredImageComponent, RouterModule, SlicePipe, CommonModule],
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
    titleColor: string;

    ngOnInit() {
        this.color = this.courseColor() || this.ARTEMIS_DEFAULT_COLOR;
        this.titleColor = getContrastingTextColor(this.color);
    }
}
