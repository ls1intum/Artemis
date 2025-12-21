import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Organization } from 'app/core/shared/entities/organization.model';
import { OrganizationManagementService } from 'app/core/admin/organization-management/organization-management.service';
import { faBan, faSave } from '@fortawesome/free-solid-svg-icons';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CustomPatternValidatorDirective } from 'app/shared/validators/custom-pattern-validator.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

/**
 * Admin component for creating and updating organizations.
 */
@Component({
    selector: 'jhi-organization-management-update',
    templateUrl: './organization-management-update.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FormsModule, TranslateDirective, CustomPatternValidatorDirective, FaIconComponent],
})
export class OrganizationManagementUpdateComponent implements OnInit {
    private readonly route = inject(ActivatedRoute);
    private readonly organizationService = inject(OrganizationManagementService);

    /** The organization being edited */
    organization: Organization = new Organization();
    /** Whether save is in progress */
    readonly isSaving = signal(false);

    protected readonly faSave = faSave;
    protected readonly faBan = faBan;

    /**
     * Enable subscriptions to retrieve the organization based on the activated route on init
     */
    ngOnInit() {
        this.isSaving.set(false);
        // create a new organization and only overwrite it if we fetch an organization to edit
        this.organization = new Organization();
        this.route.parent!.data.subscribe(({ organization }) => {
            if (organization) {
                const organizationId = organization.body ? organization.body.id : organization.id;
                this.organizationService.getOrganizationById(organizationId).subscribe((data) => {
                    this.organization = data;
                });
            }
        });
    }

    /**
     * Navigate to the previous page when the user cancels the update process
     */
    previousState() {
        window.history.back();
    }

    /**
     * Update or create user in the user management component
     */
    save() {
        this.isSaving.set(true);
        if (this.organization.id) {
            this.organizationService.update(this.organization).subscribe({
                next: () => this.onSaveSuccess(),
                error: () => this.onSaveError(),
            });
        } else {
            this.organizationService.add(this.organization).subscribe({
                next: () => this.onSaveSuccess(),
                error: () => this.onSaveError(),
            });
        }
    }

    /**
     * Set isSaving to false and navigate to previous page
     */
    private onSaveSuccess() {
        this.isSaving.set(false);
        this.previousState();
    }

    /**
     * Set isSaving to false
     */
    private onSaveError() {
        this.isSaving.set(false);
    }
}
