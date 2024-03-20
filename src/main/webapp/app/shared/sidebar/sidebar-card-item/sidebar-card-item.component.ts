import { Component, Input, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { Exercise, ExerciseType, getIcon } from 'app/entities/exercise.model';
import { Lecture } from 'app/entities/lecture.model';
import { InitializationState } from 'app/entities/participation/participation.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { BaseCardElement, ExerciseCardItem, SidebarTypes } from 'app/types/sidebar';

@Component({
    selector: 'jhi-sidebar-card-item',
    templateUrl: './sidebar-card-item.component.html',
    styleUrls: ['./sidebar-card-item.component.scss'],
})
export class SidebarCardItemComponent implements OnInit {
    private readonly initializationStatesToShowProgrammingResult = [InitializationState.INITIALIZED, InitializationState.INACTIVE, InitializationState.FINISHED];
    readonly InitializationState = InitializationState;
    readonly ExerciseType = ExerciseType;
    @Input()
    entityItem?: Exercise | Lecture | BaseCardElement | ExerciseCardItem;
    @Input() sidebarType?: SidebarTypes;

    @Input()
    noExerciseSelected?: boolean;

    projectName: string;
    gradedStudentParticipation?: StudentParticipation;
    entityIcon: IconProp;

    constructor(
        public router: Router,
        private participationService: ParticipationService,
    ) {}

    ngOnInit() {
        if (this.sidebarType === 'exercise') {
            this.entityItem as Exercise;
            if ((this.entityItem as Exercise)?.studentParticipations?.length) {
                this.gradedStudentParticipation = this.participationService.getSpecificStudentParticipation(this.entityItem.studentParticipations, false);
            }
            this.entityIcon = getIcon((this.entityItem as Exercise)?.type);
        }
    }
}
