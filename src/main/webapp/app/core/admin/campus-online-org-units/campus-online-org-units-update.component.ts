import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { faBan, faSave } from '@fortawesome/free-solid-svg-icons';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AdminTitleBarTitleDirective } from 'app/core/admin/shared/admin-title-bar-title.directive';
import { AlertService } from 'app/shared/service/alert.service';
import { CampusOnlineOrgUnit, CampusOnlineService } from 'app/core/course/manage/services/campus-online.service';

@Component({
    selector: 'jhi-campus-online-org-units-update',
    templateUrl: './campus-online-org-units-update.component.html',
    imports: [FormsModule, TranslateDirective, FaIconComponent, AdminTitleBarTitleDirective],
})
export class CampusOnlineOrgUnitsUpdateComponent implements OnInit {
    private readonly route = inject(ActivatedRoute);
    private readonly campusOnlineService = inject(CampusOnlineService);
    private readonly alertService = inject(AlertService);

    readonly orgUnit = signal<CampusOnlineOrgUnit>({ externalId: '', name: '' });
    readonly isSaving = signal(false);

    protected readonly faSave = faSave;
    protected readonly faBan = faBan;

    ngOnInit() {
        this.isSaving.set(false);
        this.route.parent!.data.subscribe(({ orgUnit }) => {
            if (orgUnit?.id) {
                this.campusOnlineService.getOrgUnit(orgUnit.id).subscribe((data) => {
                    this.orgUnit.set(data);
                });
            }
        });
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving.set(true);
        const currentOrgUnit = this.orgUnit();
        if (currentOrgUnit.id) {
            this.campusOnlineService.updateOrgUnit(currentOrgUnit).subscribe({
                next: () => this.onSaveSuccess(),
                error: (error: HttpErrorResponse) => this.onSaveError(error),
            });
        } else {
            this.campusOnlineService.createOrgUnit(currentOrgUnit).subscribe({
                next: () => this.onSaveSuccess(),
                error: (error: HttpErrorResponse) => this.onSaveError(error),
            });
        }
    }

    private onSaveSuccess() {
        this.isSaving.set(false);
        this.previousState();
    }

    private onSaveError(error: HttpErrorResponse) {
        this.isSaving.set(false);
        this.alertService.error(error.error?.message ?? error.message);
    }
}
