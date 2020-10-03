import { Component, OnInit } from '@angular/core';

@Component({
    selector: 'jhi-informative-marketing',
    templateUrl: './informative-marketing-instructors.component.html',
    styleUrls: ['./../informative-marketing.scss'],
})
export class InformativeMarketingInstructorsComponent implements OnInit {
    features: Feature[];

    constructor() {}

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
            undefined,
            'https://www.talkwalker.com/images/2020/blog-headers/image-analysis.png',
        );
        featureTwo.centerTextOne();
        const featureThree = new Feature(
            'informativeMarketing.instructor.feature.multipleExerciseTypes.title',
            'informativeMarketing.instructor.feature.multipleExerciseTypes.shortDescription',
            'informativeMarketing.instructor.feature.multipleExerciseTypes.fullDescription',
            'fa fa-code',
            undefined,
            'https://www.talkwalker.com/images/2020/blog-headers/image-analysis.png',
        );
        const featureFour = new Feature(
            'informativeMarketing.instructor.feature.multipleExerciseTypes.title',
            'informativeMarketing.instructor.feature.multipleExerciseTypes.shortDescription',
            'informativeMarketing.instructor.feature.multipleExerciseTypes.fullDescription',
            'fa fa-code',
            'informativeMarketing.instructor.feature.multipleExerciseTypes.fullDescription',
        );
        const featureFive = new Feature(
            'informativeMarketing.instructor.feature.multipleExerciseTypes.title',
            'informativeMarketing.instructor.feature.multipleExerciseTypes.shortDescription',
            'informativeMarketing.instructor.feature.multipleExerciseTypes.fullDescription',
            'fa fa-code',
            undefined,
            'https://www.talkwalker.com/images/2020/blog-headers/image-analysis.png',
            'https://cdn.eso.org/images/thumb300y/eso1907a.jpg',
        );
        const featureSix = new Feature(
            'informativeMarketing.instructor.feature.multipleExerciseTypes.title',
            'informativeMarketing.instructor.feature.multipleExerciseTypes.shortDescription',
            'informativeMarketing.instructor.feature.multipleExerciseTypes.fullDescription',
            'fa fa-code',
            'informativeMarketing.instructor.feature.multipleExerciseTypes.fullDescription',
            'https://www.talkwalker.com/images/2020/blog-headers/image-analysis.png',
            'https://cdn.eso.org/images/thumb300y/eso1907a.jpg',
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
}

class Feature {
    title: string;
    shortDescription: string;
    descriptionTextOne: string;
    textOneCentered = false;
    descriptionTextTwo?: string;
    imageOne?: string;
    imageTwo?: string;
    icon: string;
    id: string;

    constructor(title: string, shortDescription: string, descriptionTextOne: string, icon: string, descriptionTextTwo?: string, imageOne?: string, imageTwo?: string) {
        this.title = title;
        this.shortDescription = shortDescription;
        this.descriptionTextOne = descriptionTextOne;
        this.descriptionTextTwo = descriptionTextTwo;
        this.imageOne = imageOne;
        this.imageTwo = imageTwo;
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

    /**
     * Centers the text and first image.
     * Note: Only has an effect if there is no second text
     */
    centerTextOne(): void {
        this.textOneCentered = true;
    }
}
