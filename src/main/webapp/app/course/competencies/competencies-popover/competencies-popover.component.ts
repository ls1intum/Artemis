import { Component, Input, OnInit, ViewEncapsulation } from '@angular/core';
import { faFlag } from '@fortawesome/free-solid-svg-icons';
import { CompetencyLectureUnitLink } from 'app/entities/competency.model';

@Component({
    selector: 'jhi-competencies-popover',
    templateUrl: './competencies-popover.component.html',
    styleUrls: ['./competencies-popover.component.scss'],
    encapsulation: ViewEncapsulation.None,
    standalone: false,
})
export class CompetenciesPopoverComponent implements OnInit {
    @Input()
    courseId: number;
    @Input()
    competencyLinks: CompetencyLectureUnitLink[] = [];
    @Input()
    navigateTo: 'competencyManagement' | 'courseCompetencies' = 'courseCompetencies';

    navigationArray: string[] = [];

    // Icons
    faFlag = faFlag;

    ngOnInit(): void {
        if (this.courseId) {
            switch (this.navigateTo) {
                case 'courseCompetencies': {
                    this.navigationArray = ['/courses', `${this.courseId}`, 'competencies'];
                    break;
                }
                case 'competencyManagement': {
                    this.navigationArray = ['/course-management', `${this.courseId}`, 'competency-management'];
                    break;
                }
            }
        }
    }
}
