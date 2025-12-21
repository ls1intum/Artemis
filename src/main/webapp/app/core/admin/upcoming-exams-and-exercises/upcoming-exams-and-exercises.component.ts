import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { EntityArrayResponseType as ExerciseEntityArrayResponseType, ExerciseService } from 'app/exercise/services/exercise.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { RouterLink } from '@angular/router';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';

/**
 * Admin component for viewing upcoming exams and exercises across all courses.
 */
@Component({
    selector: 'jhi-upcoming-exams-and-exercises',
    templateUrl: './upcoming-exams-and-exercises.component.html',
    styles: ['.table {table-layout: fixed}'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective, RouterLink, ArtemisDatePipe],
})
export class UpcomingExamsAndExercisesComponent implements OnInit {
    private readonly exerciseService = inject(ExerciseService);
    private readonly examManagementService = inject(ExamManagementService);

    /** Upcoming exercises across all courses */
    readonly upcomingExercises = signal<Exercise[]>([]);

    /** Upcoming exams across all courses */
    readonly upcomingExams = signal<Exam[]>([]);

    ngOnInit(): void {
        this.exerciseService.getUpcomingExercises().subscribe((res: ExerciseEntityArrayResponseType) => {
            this.upcomingExercises.set(res.body ?? []);
        });

        this.examManagementService.findAllCurrentAndUpcomingExams().subscribe((res: HttpResponse<Exam[]>) => {
            this.upcomingExams.set(res.body ?? []);
        });
    }

    trackByExercise(_index: number, item: Exercise) {
        return `${item.course?.id}_${item.id}`;
    }

    trackByExam(_index: number, item: Exam) {
        return `${item.course?.id}_${item.id}`;
    }
}
