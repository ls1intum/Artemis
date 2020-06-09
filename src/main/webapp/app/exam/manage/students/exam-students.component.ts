import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { UserService } from 'app/core/user/user.service';

@Component({
    selector: 'jhi-exam-students',
    templateUrl: './exam-students.component.html',
})
export class ExamStudentsComponent implements OnInit {
    examId: number;

    constructor(private route: ActivatedRoute, private userService: UserService) {}

    /**
     * Initialize the examId
     */
    ngOnInit(): void {
        this.examId = Number(this.route.snapshot.paramMap.get('examId'));
    }
}
