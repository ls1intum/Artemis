import { Component, OnInit, inject } from '@angular/core';
import { onError } from 'app/shared/util/global.utils';
import { ActivatedRoute } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import { finalize } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { Prerequisite } from 'app/entities/prerequisite.model';
import { PrerequisiteService } from 'app/course/competencies/prerequisite.service';
import { PrerequisiteFormComponent } from 'app/course/competencies/prerequisite-form/prerequisite-form.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';

@Component({
    selector: 'jhi-create-prerequisite',
    templateUrl: './create-prerequisite.component.html',
    standalone: true,
    imports: [PrerequisiteFormComponent, ArtemisSharedModule],
})
export class CreatePrerequisiteComponent implements OnInit {
    isLoading: boolean;
    courseId: number;

    private readonly activatedRoute: ActivatedRoute = inject(ActivatedRoute);
    private readonly alertService: AlertService = inject(AlertService);
    private readonly prerequisiteService: PrerequisiteService = inject(PrerequisiteService);
    private readonly navigationUtilService: ArtemisNavigationUtilService = inject(ArtemisNavigationUtilService);

    ngOnInit(): void {
        this.isLoading = true;
        this.activatedRoute.params.subscribe((params) => {
            this.courseId = params['courseId'];
            this.isLoading = false;
        });
    }

    createPrerequisite(prerequisite: Prerequisite) {
        this.isLoading = true;
        this.prerequisiteService
            .createPrerequisite(prerequisite, this.courseId)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: () => {
                    this.navigationUtilService.navigateBack(['course-management', this.courseId, 'competency-management']);
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    cancel() {
        this.navigationUtilService.navigateBack(['course-management', this.courseId, 'competency-management']);
    }
}
