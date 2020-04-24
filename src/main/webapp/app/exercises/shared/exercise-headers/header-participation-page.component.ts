import { Component, Input, OnChanges, OnInit, ViewEncapsulation } from '@angular/core';
import * as moment from 'moment';
import { Exercise, ExerciseCategory, getIcon } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ButtonType } from 'app/shared/components/button.component';

@Component({
    selector: 'jhi-header-participation-page',
    templateUrl: './header-participation-page.component.html',
    styleUrls: ['./header-participation-page.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class HeaderParticipationPageComponent implements OnInit, OnChanges {
    readonly ButtonType = ButtonType;

    @Input() title: string;
    @Input() exercise: Exercise;
    @Input() participation: StudentParticipation;

    public exerciseStatusBadge = 'badge-success';
    public exerciseCategories: ExerciseCategory[];

    getIcon = getIcon;

    constructor(private exerciseService: ExerciseService) {}

    ngOnInit(): void {
        this.setExerciseStatusBadge();
        this.exerciseCategories = this.exerciseService.convertExerciseCategoriesFromServer(this.exercise);
    }

    ngOnChanges(): void {
        this.setExerciseStatusBadge();
        this.exerciseCategories = this.exerciseService.convertExerciseCategoriesFromServer(this.exercise);
    }

    private setExerciseStatusBadge(): void {
        if (this.exercise) {
            this.exerciseStatusBadge = moment(this.exercise.dueDate!).isBefore(moment()) ? 'badge-danger' : 'badge-success';
        }
    }
}
