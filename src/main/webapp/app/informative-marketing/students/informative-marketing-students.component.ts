import { Component, OnInit } from '@angular/core';
import { Feature } from 'app/informative-marketing/instructors/informative-marketing-instructors.component';

@Component({
    selector: 'jhi-informative-marketing',
    templateUrl: './informative-marketing-students.component.html',
    styleUrls: ['./../informative-marketing.scss'],
})
export class InformativeMarketingStudentsComponent implements OnInit {
    features: Feature[];

    constructor() {}

    ngOnInit(): void {
        const featureOne = new Feature(
            'informativeMarketing.students.feature.multipleExerciseTypes.title',
            'informativeMarketing.students.feature.multipleExerciseTypes.shortDescription',
            'informativeMarketing.students.feature.multipleExerciseTypes.fullDescription',
            'fa fa-code',
        );
        featureOne.centerTextAndImageOne();

        const featureTwo = new Feature(
            'informativeMarketing.students.feature.multipleExerciseTypes.title',
            'informativeMarketing.students.feature.multipleExerciseTypes.shortDescription',
            'informativeMarketing.students.feature.multipleExerciseTypes.fullDescription',
            'fa fa-code',
            undefined,
            'https://www.talkwalker.com/images/2020/blog-headers/image-analysis.png',
        );

        const featureThree = new Feature(
            'informativeMarketing.students.feature.multipleExerciseTypes.title',
            'informativeMarketing.students.feature.multipleExerciseTypes.shortDescription',
            'informativeMarketing.students.feature.multipleExerciseTypes.fullDescription',
            'fa fa-code',
            undefined,
            'https://www.talkwalker.com/images/2020/blog-headers/image-analysis.png',
        );
        featureThree.alignFirstImageLeft();

        const featureFour = new Feature(
            'informativeMarketing.students.feature.multipleExerciseTypes.title',
            'informativeMarketing.students.feature.multipleExerciseTypes.shortDescription',
            'informativeMarketing.students.feature.multipleExerciseTypes.fullDescription',
            'fa fa-code',
            undefined,
            'https://www.talkwalker.com/images/2020/blog-headers/image-analysis.png',
        );
        featureFour.centerTextAndImageOne();

        const featureFive = new Feature(
            'informativeMarketing.students.feature.multipleExerciseTypes.title',
            'informativeMarketing.students.feature.multipleExerciseTypes.shortDescription',
            'informativeMarketing.students.feature.multipleExerciseTypes.fullDescription',
            'fa fa-code',
            'informativeMarketing.students.feature.multipleExerciseTypes.fullDescription',
        );

        const featureSix = new Feature(
            'informativeMarketing.students.feature.multipleExerciseTypes.title',
            'informativeMarketing.students.feature.multipleExerciseTypes.shortDescription',
            'informativeMarketing.students.feature.multipleExerciseTypes.fullDescription',
            'fa fa-code',
            undefined,
            'https://www.talkwalker.com/images/2020/blog-headers/image-analysis.png',
            'https://cdn.eso.org/images/thumb300y/eso1907a.jpg',
        );

        const featureSeven = new Feature(
            'informativeMarketing.students.feature.multipleExerciseTypes.title',
            'informativeMarketing.students.feature.multipleExerciseTypes.shortDescription',
            'informativeMarketing.students.feature.multipleExerciseTypes.fullDescription',
            'fa fa-code',
            'informativeMarketing.students.feature.multipleExerciseTypes.fullDescription',
            'https://www.talkwalker.com/images/2020/blog-headers/image-analysis.png',
            'https://cdn.eso.org/images/thumb300y/eso1907a.jpg',
        );

        const featureEight = new Feature(
            'informativeMarketing.students.feature.multipleExerciseTypes.title',
            'informativeMarketing.students.feature.multipleExerciseTypes.shortDescription',
            'informativeMarketing.students.feature.multipleExerciseTypes.fullDescription',
            'fa fa-code',
            'informativeMarketing.students.feature.multipleExerciseTypes.fullDescription',
            'https://www.talkwalker.com/images/2020/blog-headers/image-analysis.png',
            'https://cdn.eso.org/images/thumb300y/eso1907a.jpg',
        );
        featureEight.alignFirstImageLeft();

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
