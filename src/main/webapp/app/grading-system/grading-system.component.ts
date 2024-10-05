import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { GradeType } from 'app/entities/grading-scale.model';
import { BaseGradingSystemComponent } from 'app/grading-system/base-grading-system/base-grading-system.component';
import { faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-grading-system',
    templateUrl: './grading-system.component.html',
    styleUrls: ['./grading-system.component.scss'],
})
export class GradingSystemComponent implements OnInit {
    private route = inject(ActivatedRoute);

    readonly GradeType = GradeType;

    courseId?: number;
    examId?: number;
    isExam = false;
    childComponent?: BaseGradingSystemComponent;

    readonly documentationType: DocumentationType = 'Grading';

    // Icons
    readonly faExclamationTriangle = faExclamationTriangle;

    ngOnInit(): void {
        this.route.params.subscribe((params) => {
            this.courseId = Number(params['courseId']);
            if (params['examId']) {
                this.examId = Number(params['examId']);
                this.isExam = true;
            }
        });
    }

    /**
     * This function gets called if the router outlet gets activated. The sub routes
     * should derive from BaseGradingSystemComponent
     * @param instance The component instance
     */
    onChildActivate(instance: BaseGradingSystemComponent) {
        this.childComponent = instance;
    }
}
