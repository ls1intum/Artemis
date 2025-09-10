import { Component, OnInit, inject } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { EntityArrayResponseType as ExerciseEntityArrayResponseType, ExerciseService } from 'app/exercise/services/exercise.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { RouterLink } from '@angular/router';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';

@Component({
    selector: 'jhi-upcoming-exams-and-exercises',
    templateUrl: './upcoming-exams-and-exercises.component.html',
    styles: ['.table {table-layout: fixed}'],
    imports: [TranslateDirective, RouterLink, ArtemisDatePipe],
})
export class UpcomingExamsAndExercisesComponent implements OnInit {
    private exerciseService = inject(ExerciseService);
    private examManagementService = inject(ExamManagementService);

    upcomingExercises: Exercise[] = [];
    upcomingExams: Exam[] = [];

    predicate: string;
    reverse: boolean;

    ngOnInit(): void {
        this.exerciseService.getUpcomingExercises().subscribe((res: ExerciseEntityArrayResponseType) => {
            this.upcomingExercises = res.body ?? [];
        });

        this.examManagementService.findAllCurrentAndUpcomingExams().subscribe((res: HttpResponse<Exam[]>) => {
            this.upcomingExams = res.body ?? [];
        });
    }

    trackByExercise(_index: number, item: Exercise) {
        return `${item.course?.id}_${item.id}`;
    }

    trackByExam(_index: number, item: Exam) {
        return `${item.course?.id}_${item.id}`;
    }
}
