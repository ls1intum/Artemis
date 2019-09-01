import { GuidedTour } from 'app/guided-tour/guided-tour.model';
import { Orientation } from 'app/guided-tour/guided-tour.constants';
import { TextTourStep } from 'app/guided-tour/guided-tour-step.model';

export const courseExerciseTour: GuidedTour = {
    settingsKey: 'course_exercise_tour',
    steps: [
        new TextTourStep({
            selector: '.course-info-bar',
            headlineTranslateKey: 'tour.course-exercise.info.headline',
            contentTranslateKey: 'tour.course-exercise.info.content',
            orientation: Orientation.BOTTOMLEFT,
        }),
        new TextTourStep({
            selector: '.problem-statement',
            headlineTranslateKey: 'tour.course-exercise.info.headline',
            contentTranslateKey: 'tour.course-exercise.info.content',
            orientation: Orientation.TOPRIGHT,
        }),
        new TextTourStep({
            selector: 'jhi-student-questions .panel-wrapper',
            headlineTranslateKey: 'tour.course-exercise.info.headline',
            contentTranslateKey: 'tour.course-exercise.info.content',
            orientation: Orientation.LEFT,
        }),
        new TextTourStep({
            selector: '.start-exercise',
            headlineTranslateKey: 'tour.course-exercise.info.headline',
            contentTranslateKey: 'tour.course-exercise.info.content',
            orientation: Orientation.RIGHT,
            highlightPadding: 10,
        }),
    ],
};
