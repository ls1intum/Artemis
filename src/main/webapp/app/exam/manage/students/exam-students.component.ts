import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { UserService } from 'app/core/user/user.service';

@Component({
    selector: 'jhi-exam-students',
    templateUrl: './exam-students.component.html',
})
export class ExamStudentsComponent implements OnInit {
    courseId: number;
    examId: number;

    constructor(private route: ActivatedRoute, private userService: UserService) {}

    /**
     * Initialize the courseId and examId
     */
    ngOnInit(): void {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.examId = Number(this.route.snapshot.paramMap.get('examId'));
    }
}
