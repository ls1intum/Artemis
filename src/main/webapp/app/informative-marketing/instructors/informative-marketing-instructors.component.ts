import { Component, OnInit } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

@Component({
    selector: 'jhi-informative-marketing',
    templateUrl: './informative-marketing-instructors.component.html',
    styleUrls: ['./../informative-marketing.scss'],
})
export class InformativeMarketingInstructorsComponent implements OnInit {
    features: Feature[];

    constructor(private translateService: TranslateService) {}

    ngOnInit(): void {
        const multipleExerciseTypes = new Feature(
            'informativeMarketing.instructor.feature.multipleExerciseTypes.title',
            'informativeMarketing.instructor.feature.multipleExerciseTypes.shortDescription',
            'informativeMarketing.instructor.feature.multipleExerciseTypes.fullDescription',
        );
        this.features = [
            multipleExerciseTypes,
            multipleExerciseTypes,
            multipleExerciseTypes,
            multipleExerciseTypes,
            multipleExerciseTypes,
            multipleExerciseTypes,
            multipleExerciseTypes,
            multipleExerciseTypes,
        ];
    }

    translateFeatureTitle(feature: Feature) {
        return this.translateService.instant(feature.title);
    }

    translateFeatureShortDescription(feature: Feature) {
        return this.translateService.instant(feature.shortDescription);
    }

    translateFeatureDescription(feature: Feature) {
        return this.translateService.instant(feature.longDescription);
    }
}

class Feature {
    title: string;
    shortDescription: string;
    longDescription: string;

    constructor(title: string, shortDescription: string, longDescription: string) {
        this.title = title;
        this.shortDescription = shortDescription;
        this.longDescription = longDescription;
    }
}
