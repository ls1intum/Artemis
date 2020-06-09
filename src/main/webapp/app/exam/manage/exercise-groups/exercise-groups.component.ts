import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';

@Component({
    selector: 'jhi-exercise-groups',
    templateUrl: './exercise-groups.component.html',
})
export class ExerciseGroupsComponent implements OnInit {
    examId: number;

    constructor(private route: ActivatedRoute, private exerciseGroupService: ExerciseGroupService) {}

    /**
     * Initialize the examId
     */
    ngOnInit(): void {
        this.examId = Number(this.route.snapshot.paramMap.get('examId'));
    }
}
