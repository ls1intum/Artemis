import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { DocumentationButtonComponent, DocumentationType } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { GradeType } from 'app/assessment/shared/entities/grading-scale.model';
import { BaseGradingSystemComponent } from 'app/assessment/manage/grading-system/base-grading-system/base-grading-system.component';
import { faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';

import { TranslateDirective } from 'app/shared/language/translate.directive';
import { GradingSystemInfoModalComponent } from 'app/assessment/manage/grading-system/grading-system-info-modal/grading-system-info-modal.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FormsModule } from '@angular/forms';
import { GradingSystemPresentationsComponent } from 'app/assessment/manage/grading-system/grading-system-presentations/grading-system-presentations.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';

@Component({
    selector: 'jhi-grading-system',
    templateUrl: './grading-system.component.html',
    styleUrls: ['./grading-system.component.scss'],
    imports: [
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
        HelpIconComponent,
        DocumentationButtonComponent,
    ],
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
