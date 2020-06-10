import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';

@Component({
    selector: 'jhi-exercise-group-detail',
    templateUrl: './exercise-group-detail.component.html',
})
export class ExerciseGroupDetailComponent implements OnInit {
    courseId: number;
    exerciseGroup: ExerciseGroup;

    constructor(private route: ActivatedRoute, private exerciseGroupService: ExerciseGroupService) {}

    /**
     * Initialize the courseId and exerciseGroup
     */
    ngOnInit(): void {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.route.data.subscribe(({ exerciseGroup }) => (this.exerciseGroup = exerciseGroup));
    }
}
