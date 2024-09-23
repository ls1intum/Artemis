import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Lecture } from 'app/entities/lecture.model';
import { CourseCompetencyFormData } from 'app/course/competencies/forms/course-competency-form.component';

@Component({ selector: 'jhi-competency-form', standalone: true, template: '' })
export class CompetencyFormStubComponent {
    @Input()
    formData: CourseCompetencyFormData;
    @Input()
    isEditMode = false;
    @Input()
    isInConnectMode = false;
    @Input()
    isInSingleLectureMode = false;
    @Input()
    courseId: number;
    @Input()
    lecturesOfCourseWithLectureUnits: Lecture[] = [];
    @Input()
    averageStudentScore?: number;
    @Input()
    hasCancelButton: boolean;

    @Output()
    onCancel: EventEmitter<any> = new EventEmitter<any>();
    @Output()
    formSubmitted: EventEmitter<CourseCompetencyFormData> = new EventEmitter<CourseCompetencyFormData>();
}
