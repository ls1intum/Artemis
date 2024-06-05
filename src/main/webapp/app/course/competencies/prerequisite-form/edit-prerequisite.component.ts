import { Component, OnInit } from '@angular/core';
import { onError } from 'app/shared/util/global.utils';
import { ActivatedRoute, Router } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import { finalize, switchMap } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { Prerequisite } from 'app/entities/prerequisite.model';
import { PrerequisiteService } from 'app/course/competencies/prerequisite.service';
import { PrerequisiteFormComponent } from 'app/course/competencies/prerequisite-form/prerequisite-form.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@Component({
    selector: 'jhi-edit-prerequisite',
    templateUrl: './edit-prerequisite.component.html',
    standalone: true,
    styles: [],
    imports: [PrerequisiteFormComponent, ArtemisSharedModule],
})
export class EditPrerequisiteComponent implements OnInit {
    isLoading: boolean;
    courseId: number;
    existingPrerequisite: Prerequisite;

    constructor(
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private alertService: AlertService,
        private prerequisiteService: PrerequisiteService,
    ) {}

    ngOnInit(): void {
        this.isLoading = true;
        this.activatedRoute.params
            .pipe(
                switchMap((params) => {
                    console.log(params);
                    const prerequisiteId = Number(params['prerequisiteId']);
                    this.courseId = params['courseId'];
                    return this.prerequisiteService.getPrerequisite(prerequisiteId, this.courseId);
                }),
            )
            .subscribe({
                next: (prerequisite) => {
                    this.existingPrerequisite = prerequisite;
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
                    this.router.navigate(['../../'], { relativeTo: this.activatedRoute });
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    cancel() {
        this.router.navigate(['../../'], { relativeTo: this.activatedRoute });
    }
}
