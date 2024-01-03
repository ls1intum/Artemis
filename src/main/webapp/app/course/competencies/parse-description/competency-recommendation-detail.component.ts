import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { CompetencyTaxonomy, CompetencyValidators } from 'app/entities/competency.model';
import { faChevronRight, faEdit, faSave, faTrash, faWrench } from '@fortawesome/free-solid-svg-icons';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { FormGroup, Validators } from '@angular/forms';
import { CompetencyFormControlsWithViewed } from 'app/course/competencies/parse-description/parse-course-description.component';

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
    protected readonly faEdit = faEdit;

    //Other constants for html
    protected readonly competencyTaxonomy = CompetencyTaxonomy;
    protected readonly competencyValidators = CompetencyValidators;
    protected readonly ButtonType = ButtonType;
    protected readonly ButtonSize = ButtonSize;

    ngOnInit(): void {
        this.titleControl.addValidators([Validators.required, Validators.maxLength(CompetencyValidators.TITLE_MAX)]);
        this.descriptionControl.addValidators([Validators.maxLength(CompetencyValidators.DESCRIPTION_MAX)]);
        //disable all competency controls as component is not in edit mode
        this.form.controls.competency.disable();
        //viewed checkbox is always disabled and only updated through toggle/edit
        this.viewedControl.disable();
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
     * Keeps order of elements as-is in the keyvalue pipe
     */
    keepOrder = () => {
        return 0;
    };

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

    get viewedControl() {
        return this.form.controls.viewed;
    }
}
