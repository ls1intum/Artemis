import { Component, OnInit } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { EntityArrayResponseType as ExerciseEntityArrayResponseType, ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { Exercise } from 'app/entities/exercise.model';
import { SortService } from 'app/shared/service/sort.service';
import { Exam } from 'app/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';

@Component({
    selector: 'jhi-upcoming-exams-and-exercises',
    templateUrl: './upcoming-exams-and-exercises.component.html',
    styles: ['.table {table-layout: fixed}'],
})
export class UpcomingExamsAndExercisesComponent implements OnInit {
    upcomingExercises: Exercise[] = [];
    upcomingExams: Exam[] = [];

    predicate: string;
    reverse: boolean;

    constructor(private exerciseService: ExerciseService, private examManagementService: ExamManagementService, private sortService: SortService) {}

    ngOnInit(): void {
        this.exerciseService.getUpcomingExercises().subscribe((res: ExerciseEntityArrayResponseType) => {
            this.upcomingExercises = res.body ?? [];
        });

        this.examManagementService.findAllCurrentAndUpcomingExams().subscribe((res: HttpResponse<Exam[]>) => {
            this.upcomingExams = res.body ?? [];
        });
    }

    trackByExercise(index: number, item: Exercise) {
        return `${item.course?.id}_${item.id}`;
    }

    trackByExam(index: number, item: Exam) {
        return `${item.course?.id}_${item.id}`;
    }
}
