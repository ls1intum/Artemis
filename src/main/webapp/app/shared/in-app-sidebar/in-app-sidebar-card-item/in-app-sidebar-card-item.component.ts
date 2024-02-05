import { Component, Input, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faEdit } from '@fortawesome/free-solid-svg-icons';
import { ExerciseType, getIcon } from 'app/entities/exercise.model';
import { InitializationState } from 'app/entities/participation/participation.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';

@Component({
    selector: 'jhi-in-app-sidebar-card-item',
    templateUrl: './in-app-sidebar-card-item.component.html',
    styleUrls: ['./in-app-sidebar-card-item.component.scss'],
})
export class InAppSidebarCardItemComponent implements OnInit {
    private readonly initializationStatesToShowProgrammingResult = [InitializationState.INITIALIZED, InitializationState.INACTIVE, InitializationState.FINISHED];
    readonly InitializationState = InitializationState;
    readonly ExerciseType = ExerciseType;
    @Input()
    entityItem?: any;

    @Input() studentParticipation?: StudentParticipation;

    @Input()
    noExerciseSelected?: boolean;

    faEdit = faEdit;
    projectName: string;
    gradedStudentParticipation?: StudentParticipation;
    entityIcon: IconProp;

    constructor(
        public router: Router,
        private participationService: ParticipationService,
    ) {}

    ngOnInit() {
        if (this.entityItem?.studentParticipations?.length) {
            this.gradedStudentParticipation = this.participationService.getSpecificStudentParticipation(this.entityItem.studentParticipations, false);
        }
        this.entityIcon = getIcon(this.entityItem.type);
    }
}
