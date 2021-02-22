import { Component, HostBinding, Input, OnChanges, SimpleChanges } from '@angular/core';
import * as moment from 'moment';
import { Moment } from 'moment';
import { Course } from 'app/entities/course.model';
import { ActivatedRoute, Router } from '@angular/router';
import { Lecture } from 'app/entities/lecture.model';
import { LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';

@Component({
    selector: 'jhi-course-lecture-row',
    templateUrl: './course-lecture-row.component.html',
    styleUrls: ['../course-exercises/course-exercise-row.scss'],
})
export class CourseLectureRowComponent implements OnChanges {
    @HostBinding('class') classes = 'exercise-row';
    @Input() lecture: Lecture;
    @Input() course: Course;
    @Input() extendedLink = false;

    noOfExerciseUnits = 0;
    noOfTextUnits = 0;
    noOfVideoUnits = 0;
    noOfAttachmentUnits = 0;

    constructor(private router: Router, private route: ActivatedRoute) {}

    ngOnChanges(changes: SimpleChanges) {
        for (const propName in changes) {
            if (changes.hasOwnProperty(propName)) {
                switch (propName) {
                    case 'lecture': {
                        if (this.lecture && this.lecture.lectureUnits) {
                            this.noOfExerciseUnits = 0;
                            this.noOfTextUnits = 0;
                            this.noOfVideoUnits = 0;
                            this.noOfAttachmentUnits = 0;
                            for (const lectureUnit of this.lecture.lectureUnits) {
                                switch (lectureUnit.type) {
                                    case LectureUnitType.ATTACHMENT:
                                        this.noOfAttachmentUnits += 1;
                                        break;
                                    case LectureUnitType.EXERCISE:
                                        this.noOfExerciseUnits += 1;
                                        break;
                                    case LectureUnitType.TEXT:
                                        this.noOfTextUnits += 1;
                                        break;
                                    case LectureUnitType.VIDEO:
                                        this.noOfVideoUnits += 1;
                                        break;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    getUrgentClass(date?: Moment) {
        if (!date) {
            return undefined;
        }
        const remainingDays = date.diff(moment(), 'days');
        if (0 <= remainingDays && remainingDays < 7) {
            return 'text-danger';
        }
    }

    showDetails() {
        const lectureToAttach = {
            ...this.lecture,
            startDate: this.lecture.startDate ? this.lecture.startDate.valueOf() : undefined,
            endDate: this.lecture.endDate ? this.lecture.endDate.valueOf() : undefined,
            course: {
                id: this.course.id,
            },
        };
        if (this.extendedLink) {
            this.router.navigate(['courses', this.course.id, 'lectures', this.lecture.id], {
                state: {
                    lecture: lectureToAttach,
                },
            });
        } else {
            this.router.navigate([this.lecture.id], {
                relativeTo: this.route,
                state: {
                    lecture: lectureToAttach,
                },
            });
        }
    }
}
