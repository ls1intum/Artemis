import { Component, OnInit, ViewEncapsulation, input, signal } from '@angular/core';
import { faFlag } from '@fortawesome/free-solid-svg-icons';
import { CompetencyLectureUnitLink } from 'app/atlas/shared/entities/competency.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { RouterLink } from '@angular/router';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-competencies-popover',
    templateUrl: './competencies-popover.component.html',
    styleUrls: ['./competencies-popover.component.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [TranslateDirective, RouterLink, NgbPopover, FaIconComponent],
})
export class CompetenciesPopoverComponent implements OnInit {
    courseId = input<number>();
    competencyLinks = input<CompetencyLectureUnitLink[]>([]);
    navigateTo = input<'competencyManagement' | 'courseCompetencies'>('courseCompetencies');

    readonly navigationArray = signal<string[]>([]);

    // Icons
    faFlag = faFlag;

    ngOnInit(): void {
        if (this.courseId()) {
            switch (this.navigateTo()) {
                case 'courseCompetencies': {
                    this.navigationArray.set(['/courses', `${this.courseId()}`, 'competencies']);
                    break;
                }
                case 'competencyManagement': {
                    this.navigationArray.set(['/course-management', `${this.courseId()}`, 'competency-management']);
                    break;
                }
            }
        }
    }
}
