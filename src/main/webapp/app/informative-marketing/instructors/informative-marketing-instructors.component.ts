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
        const featureOne = new Feature(
            'informativeMarketing.instructor.feature.multipleExerciseTypes.title',
            'informativeMarketing.instructor.feature.multipleExerciseTypes.shortDescription',
            'informativeMarketing.instructor.feature.multipleExerciseTypes.fullDescription',
            'fa fa-code',
        );
        const featureTwo = new Feature(
            'informativeMarketing.instructor.feature.multipleExerciseTypes.title',
            'informativeMarketing.instructor.feature.multipleExerciseTypes.shortDescription',
            'informativeMarketing.instructor.feature.multipleExerciseTypes.fullDescription',
            'fa fa-code',
        );
        const featureThree = new Feature(
            'informativeMarketing.instructor.feature.multipleExerciseTypes.title',
            'informativeMarketing.instructor.feature.multipleExerciseTypes.shortDescription',
            'informativeMarketing.instructor.feature.multipleExerciseTypes.fullDescription',
            'fa fa-code',
        );
        const featureFour = new Feature(
            'informativeMarketing.instructor.feature.multipleExerciseTypes.title',
            'informativeMarketing.instructor.feature.multipleExerciseTypes.shortDescription',
            'informativeMarketing.instructor.feature.multipleExerciseTypes.fullDescription',
            'fa fa-code',
        );
        const featureFive = new Feature(
            'informativeMarketing.instructor.feature.multipleExerciseTypes.title',
            'informativeMarketing.instructor.feature.multipleExerciseTypes.shortDescription',
            'informativeMarketing.instructor.feature.multipleExerciseTypes.fullDescription',
            'fa fa-code',
        );
        const featureSix = new Feature(
            'informativeMarketing.instructor.feature.multipleExerciseTypes.title',
            'informativeMarketing.instructor.feature.multipleExerciseTypes.shortDescription',
            'informativeMarketing.instructor.feature.multipleExerciseTypes.fullDescription',
            'fa fa-code',
        );
        const featureSeven = new Feature(
            'informativeMarketing.instructor.feature.multipleExerciseTypes.title',
            'informativeMarketing.instructor.feature.multipleExerciseTypes.shortDescription',
            'informativeMarketing.instructor.feature.multipleExerciseTypes.fullDescription',
            'fa fa-code',
        );
        const featureEight = new Feature(
            'informativeMarketing.instructor.feature.multipleExerciseTypes.title',
            'informativeMarketing.instructor.feature.multipleExerciseTypes.shortDescription',
            'informativeMarketing.instructor.feature.multipleExerciseTypes.fullDescription',
            'fa fa-code',
        );
        this.features = [featureOne, featureTwo, featureThree, featureFour, featureFive, featureSix, featureSeven, featureEight];
    }

    navigateToFeature(featureId: string): void {
        // get html element for feature
        const element = document.getElementById('feature' + featureId);
        if (element) {
            // scroll to correct y
            const y = element.getBoundingClientRect().top + window.pageYOffset;
            window.scrollTo({ top: y, behavior: 'smooth' });
        }
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
    icon: string;
    id: string;

    constructor(title: string, shortDescription: string, longDescription: string, icon: string) {
        this.title = title;
        this.shortDescription = shortDescription;
        this.longDescription = longDescription;
        this.icon = icon;
        this.id = this.setId();
    }

    /**
     * Math.random should be unique because of its seeding algorithm.
     * Convert it to base 36 (numbers + letters), and grab the first 9 characters after the decimal.
     * @private
     */
    setId(): string {
        return ':' + Math.random().toString(36).substr(2, 9);
    }
}
