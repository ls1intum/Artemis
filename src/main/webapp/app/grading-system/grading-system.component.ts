import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { GradeType } from 'app/entities/grading-scale.model';
import { BaseGradingSystemComponent } from 'app/grading-system/base-grading-system/base-grading-system.component';
import { faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { GradingSystemInfoModalComponent } from './grading-system-info-modal/grading-system-info-modal.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FormsModule } from '@angular/forms';
import { GradingSystemPresentationsComponent } from './grading-system-presentations/grading-system-presentations.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-grading-system',
    templateUrl: './grading-system.component.html',
    styleUrls: ['./grading-system.component.scss'],
    standalone: true,
    imports: [
        ArtemisSharedComponentModule,
        TranslateDirective,
        GradingSystemInfoModalComponent,
        FaIconComponent,
        NgbTooltip,
        FormsModule,
        GradingSystemPresentationsComponent,
        RouterLink,
        RouterLinkActive,
        RouterOutlet,
        ArtemisTranslatePipe,
    ],
})
export class GradingSystemComponent implements OnInit {
    readonly GradeType = GradeType;

    courseId?: number;
    examId?: number;
    isExam = false;
    childComponent?: BaseGradingSystemComponent;

    readonly documentationType: DocumentationType = 'Grading';

    // Icons
    readonly faExclamationTriangle = faExclamationTriangle;

    constructor(private route: ActivatedRoute) {}

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
