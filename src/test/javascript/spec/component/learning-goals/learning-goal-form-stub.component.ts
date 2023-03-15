import { Component, EventEmitter, Input, Output } from '@angular/core';

import { LearningGoalFormData } from 'app/course/learning-goals/learning-goal-form/learning-goal-form.component';
import { Lecture } from 'app/entities/lecture.model';

@Component({ selector: 'jhi-learning-goal-form', template: '' })
export class LearningGoalFormStubComponent {
    @Input() formData: LearningGoalFormData;
    @Input() courseId: number;
    @Input() isEditMode = false;
    @Input() lecturesOfCourseWithLectureUnits: Lecture[] = [];
    @Input() averageStudentScore?: number;
    @Output() formSubmitted: EventEmitter<LearningGoalFormData> = new EventEmitter<LearningGoalFormData>();
}
