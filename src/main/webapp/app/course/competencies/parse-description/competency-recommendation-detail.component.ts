import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { CompetencyTaxonomy, CompetencyValidators } from 'app/entities/competency.model';
import { faChevronRight, faEdit, faSave, faTrash, faWrench } from '@fortawesome/free-solid-svg-icons';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { FormGroup, Validators } from '@angular/forms';

@Component({
    selector: 'jhi-competency-recommendation',
    templateUrl: './competency-recommendation-detail.component.html',
    styleUrls: ['competency-recommendation-detail.component.scss'],
})
export class CompetencyRecommendationDetailComponent implements OnInit {
    //TODO: Specify type of FormGroup?
    @Input({ required: true }) form: FormGroup;
    @Input({ required: true }) index: number;
    seen = false;

    @Input() isCollapsed = true;
    isInEditMode = false;

    @Output()
    onDelete: EventEmitter<void> = new EventEmitter<void>();

    @Output()
    onSeen: EventEmitter<void> = new EventEmitter<void>();

    //Icons
    protected readonly faChevronRight = faChevronRight;
    protected readonly faTrash = faTrash;
    protected readonly faWrench = faWrench;
    protected readonly faSave = faSave;

    toggle() {
        this.isCollapsed = !this.isCollapsed;
        this.seen = true;
        this.onSeen.emit();
    }
    constructor() {}

    ngOnInit(): void {
        this.titleControl?.addValidators([Validators.required, Validators.maxLength(this.competencyValidators.TITLE_MAX)]);
        this.descriptionControl?.addValidators([Validators.maxLength(this.competencyValidators.DESCRIPTION_MAX)]);
        this.form.disable();
    }

    delete() {
        this.onDelete.emit();
    }

    edit() {
        this.form.enable();
        this.isInEditMode = true;
        this.isCollapsed = false;
        this.seen = true;
        this.onSeen.emit();
    }

    save() {
        this.form.disable();
        this.isInEditMode = false;
        this.isCollapsed = true;
    }

    /**
     * Keeps order of elements as-is in the keyvalue pipe
     */
    keepOrder = () => {
        return 0;
    };

    get titleControl() {
        return this.form.get('title');
    }

    get descriptionControl() {
        return this.form.get('description');
    }

    get isSavePossible() {
        return !this.form.invalid;
    }

    protected readonly competencyTaxonomy = CompetencyTaxonomy;
    protected readonly competencyValidators = CompetencyValidators;
    protected readonly ButtonType = ButtonType;
    protected readonly ButtonSize = ButtonSize;
    protected readonly faEdit = faEdit;
}
