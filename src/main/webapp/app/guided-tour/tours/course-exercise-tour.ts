import { GuidedTour } from 'app/guided-tour/guided-tour.model';
import { ContentType, Orientation } from 'app/guided-tour/guided-tour.constants';

export const courseExerciseTour: GuidedTour = {
    settingsId: 'showCourseExerciseTour',
    steps: [
        {
            contentType: ContentType.TEXT,
            selector: '.course-info-bar',
            headlineTranslateKey: 'tour.course-exercise.info.headline',
            contentTranslateKey: 'tour.course-exercise.info.content',
            orientation: Orientation.BOTTOMLEFT,
        },
        {
            contentType: ContentType.TEXT,
            selector: '.problem-statement',
            headlineTranslateKey: 'tour.course-exercise.info.headline',
            contentTranslateKey: 'tour.course-exercise.info.content',
            orientation: Orientation.BOTTOMLEFT,
        },
        {
            contentType: ContentType.TEXT,
            selector: '.no-results',
            headlineTranslateKey: 'tour.course-exercise.info.headline',
            contentTranslateKey: 'tour.course-exercise.info.content',
            orientation: Orientation.BOTTOMLEFT,
        },
        {
            contentType: ContentType.TEXT,
            selector: 'jhi-student-questions .panel-wrapper',
            headlineTranslateKey: 'tour.course-exercise.info.headline',
            contentTranslateKey: 'tour.course-exercise.info.content',
            orientation: Orientation.LEFT,
        },
        {
            contentType: ContentType.TEXT,
            selector: '.start-exercise',
            headlineTranslateKey: 'tour.course-exercise.info.headline',
            contentTranslateKey: 'tour.course-exercise.info.content',
            orientation: Orientation.RIGHT,
            highlightPadding: 10,
        },
    ],
};
