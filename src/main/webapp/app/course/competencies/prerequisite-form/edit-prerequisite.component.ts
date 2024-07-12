import { Component, OnInit, inject } from '@angular/core';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { finalize, switchMap } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { Prerequisite } from 'app/entities/prerequisite.model';
import { PrerequisiteService } from 'app/course/competencies/prerequisite.service';
import { PrerequisiteFormComponent } from 'app/course/competencies/prerequisite-form/prerequisite-form.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ActivatedRoute, Router } from '@angular/router';

@Component({
    selector: 'jhi-edit-prerequisite',
    templateUrl: './edit-prerequisite.component.html',
    standalone: true,
    imports: [PrerequisiteFormComponent, ArtemisSharedModule],
})
export class EditPrerequisiteComponent implements OnInit {
    isLoading: boolean;
    courseId: number;
    existingPrerequisite: Prerequisite;

    private readonly activatedRoute: ActivatedRoute = inject(ActivatedRoute);
    private readonly alertService: AlertService = inject(AlertService);
    private readonly prerequisiteService: PrerequisiteService = inject(PrerequisiteService);
    private readonly router: Router = inject(Router);

    ngOnInit(): void {
        this.isLoading = true;
        this.activatedRoute.params
            .pipe(
                switchMap((params) => {
                    const prerequisiteId = Number(params['prerequisiteId']);
                    this.courseId = Number(params['courseId']);
                    return this.prerequisiteService.getPrerequisite(prerequisiteId, this.courseId);
                }),
            )
            .subscribe({
                next: (prerequisite) => {
                    this.existingPrerequisite = prerequisite;
                    this.isLoading = false;
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    updatePrerequisite(prerequisite: Prerequisite) {
        this.isLoading = true;
        this.prerequisiteService
            .updatePrerequisite(prerequisite, this.existingPrerequisite.id!, this.courseId)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: () => {
                    this.router.navigate(['course-management', this.courseId, 'competency-management']);
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    cancel() {
        this.router.navigate(['course-management', this.courseId, 'competency-management']);
    }
}
