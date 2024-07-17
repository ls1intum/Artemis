import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Lecture } from 'app/entities/lecture.model';
import { CourseCompetencyFormData } from 'app/course/competencies/forms/course-competency-form.component';

@Component({ selector: 'jhi-competency-form', standalone: true, template: '' })
export class CompetencyFormStubComponent {
    @Input() formData: CourseCompetencyFormData;
    @Input() courseId: number;
    @Input() isEditMode = false;
    @Input() lecturesOfCourseWithLectureUnits: Lecture[] = [];
    @Input() averageStudentScore?: number;
    @Output() formSubmitted: EventEmitter<CourseCompetencyFormData> = new EventEmitter<CourseCompetencyFormData>();
}
