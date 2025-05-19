import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';

@Component({
    selector: 'jhi-course-practice',
    imports: [ButtonComponent],
    templateUrl: './course-practice.component.html',
    styleUrl: './course-practice.component.scss',
})
export class CoursePracticeComponent implements OnInit {
    private router = inject(Router);
    private route = inject(ActivatedRoute);

    courseId: number;

    ngOnInit(): void {
        this.route.parent?.params.subscribe((params) => {
            this.courseId = Number(params['courseId']);
        });
    }

    public navigateToPractice(): void {
        this.router.navigate(['courses', this.courseId, 'quiz']);
    }
}
