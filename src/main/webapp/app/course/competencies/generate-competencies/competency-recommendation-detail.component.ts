import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { CourseCompetencyValidators } from 'app/entities/competency.model';
import { faChevronRight, faPencilAlt, faSave, faTrash, faWrench } from '@fortawesome/free-solid-svg-icons';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { FormGroup, Validators } from '@angular/forms';
import { CompetencyFormControlsWithViewed } from 'app/course/competencies/generate-competencies/generate-competencies.component';

@Component({
    selector: 'jhi-competency-recommendation',
    templateUrl: './competency-recommendation-detail.component.html',
    styleUrls: ['competency-recommendation-detail.component.scss'],
})
export class CompetencyRecommendationDetailComponent implements OnInit {
    @Input({ required: true }) form: FormGroup<CompetencyFormControlsWithViewed>;
    @Input({ required: true }) index: number;
    @Input() isCollapsed = true;
    isInEditMode = false;

    @Output() onDelete: EventEmitter<void> = new EventEmitter<void>();

    //Icons
    protected readonly faChevronRight = faChevronRight;
    protected readonly faTrash = faTrash;
    protected readonly faWrench = faWrench;
    protected readonly faSave = faSave;
    protected readonly faPencilAlt = faPencilAlt;

    //Other constants for html
    protected readonly competencyValidators = CourseCompetencyValidators;
    protected readonly ButtonType = ButtonType;
    protected readonly ButtonSize = ButtonSize;

    ngOnInit(): void {
        this.titleControl.addValidators([Validators.required, Validators.maxLength(CourseCompetencyValidators.TITLE_MAX)]);
        this.descriptionControl.addValidators([Validators.maxLength(CourseCompetencyValidators.DESCRIPTION_MAX)]);
        //disable all competency controls as component is not in edit mode
        this.form.controls.competency.disable();
        //viewed checkbox is always enabled
        this.viewedControl.enable();
    }

    /**
     * Toggles collapsed status and sets viewed to true
     */
    toggle() {
        this.isCollapsed = !this.isCollapsed;
        this.viewedControl.setValue(true);
    }

    /**
     * Sends event to parent to handle delete
     */
    delete() {
        this.onDelete.emit();
    }

    /**
     * Enters edit mode: Enables all form fields and expands the element
     */
    edit() {
        this.form.controls.competency.enable();
        this.isInEditMode = true;
        this.isCollapsed = false;
        this.viewedControl.setValue(true);
    }

    /**
     * Leaves edit mode: Disables all form fields again and collapses the element
     */
    save() {
        this.form.controls.competency.disable();
        this.isInEditMode = false;
        this.isCollapsed = true;
    }

    /**
     * Updates description form on markdown change
     * @param content markdown content
     */
    updateDescriptionControl(content: string) {
        this.descriptionControl.setValue(content);
        this.descriptionControl.markAsDirty();
    }

    /**
     * Only allows save if no form controls have validation errors
     */
    get isSavePossible() {
        return !this.form.invalid;
    }

    //getters for the form controls

    get titleControl() {
        return this.form.controls.competency.controls.title;
    }

    get descriptionControl() {
        return this.form.controls.competency.controls.description;
    }

    get taxonomyControl() {
        return this.form.controls.competency.controls.taxonomy;
    }

    get viewedControl() {
        return this.form.controls.viewed;
    }
}
