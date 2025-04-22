import { Component, OnInit, inject, input } from '@angular/core';
import { ExerciseType, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Router } from '@angular/router';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';

@Component({
    template: '',
})
export abstract class ExerciseManageButtonComponent implements OnInit {
    course = input<Course | undefined>();
    exerciseType = input<ExerciseType>();
    translationLabel: string;
    translationKey = input<string | undefined>();
    featureToggle = input<FeatureToggle | undefined>();

    protected router = inject(Router);
    protected modalService = inject(NgbModal);

    icon: IconProp;
    ngOnInit(): void {
        if (!this.exerciseType()) {
            return;
        }
        this.icon = getIcon(this.exerciseType());

        if (this.translationKey()) {
            this.translationLabel = this.translationKey()!;
        } else {
            this.setTranslationLabel();
        }
    }
    protected abstract getTranslationSuffix(): string;

    setTranslationLabel(): void {
        const translationSuffix = this.getTranslationSuffix();
        if (this.exerciseType() === ExerciseType.FILE_UPLOAD) {
            this.translationLabel = `artemisApp.fileUploadExercise.home.${translationSuffix}`;
        } else {
            this.translationLabel = 'artemisApp.' + this.exerciseType() + `Exercise.home.${translationSuffix}`;
        }
    }
}
