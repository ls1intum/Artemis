import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';

@Component({
    selector: 'jhi-exercise-group-detail',
    templateUrl: './exercise-group-detail.component.html',
})
export class ExerciseGroupDetailComponent implements OnInit {
    exerciseGroup: ExerciseGroup;

    constructor(private route: ActivatedRoute, private exerciseGroupService: ExerciseGroupService) {}

    /**
     * Initialize the exerciseGroup
     */
    ngOnInit(): void {
        this.route.data.subscribe(({ exerciseGroup }) => (this.exerciseGroup = exerciseGroup));
    }
}
