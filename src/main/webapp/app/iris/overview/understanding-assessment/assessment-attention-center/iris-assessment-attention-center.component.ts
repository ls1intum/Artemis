import { Component, Input, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Course } from 'app/course/shared/entities/course.model';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { faBrain } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { CardWrapperComponent } from 'app/shared-ui/card-wrapper/card-wrapper.component';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { IrisAssessmentAttentionDTO } from 'app/iris/shared/entities/iris-assessment-attention-dto.model';
import { HttpResponse } from '@angular/common/http';
import { FeatureToggle } from 'app/foundation/feature-toggle/feature-toggle.service';
import { FeatureToggleDirective } from 'app/foundation/feature-toggle/feature-toggle.directive';
import { RouterModule } from '@angular/router';
import { ButtonComponent, ButtonSize, ButtonType } from 'app/shared-ui/components/buttons/button/button.component';

@Component({
    selector: 'jhi-iris-assessment-attention-center',
    imports: [HelpIconComponent, FaIconComponent, CardWrapperComponent, FeatureToggleDirective, RouterModule, CommonModule, ButtonComponent],
    templateUrl: './iris-assessment-attention-center.component.html',
})
export class IrisAssessmentAttentionCenterComponent implements OnInit {
    protected readonly faBrain = faBrain;

    @Input()
    course: Course;
    @Input()
    assessmentEnabled: boolean;
    @Input()
    hideLabelMobile = false;

    needsAttention = false;

    private courseManagementService = inject(CourseManagementService);

    ngOnInit() {
        this.courseManagementService.getAssessmentAttentionState(this.course.id!).subscribe((response: HttpResponse<IrisAssessmentAttentionDTO>) => {
            this.needsAttention = response.body!.needsAttention;
        });
    }

    protected readonly FeatureToggle = FeatureToggle;
    protected readonly ButtonSize = ButtonSize;
    protected readonly ButtonType = ButtonType;
}
