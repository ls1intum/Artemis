import { Orientation, UserInteractionEvent } from '../../src/main/webapp/app/guided-tour/guided-tour.constants';
import { GuidedTour } from '../../src/main/webapp/app/guided-tour/guided-tour.model';
import { ImageTourStep, ModelingTaskTourStep, TextTourStep, VideoTourStep } from '../../src/main/webapp/app/guided-tour/guided-tour-step.model';
import { GuidedTourModelingTask, personUML } from '../../src/main/webapp/app/guided-tour/guided-tour-task.model';

export const exampleTutorial: GuidedTour = {
    settingsKey: 'example_tutorial',
    steps: [
        new TextTourStep({
            highlightSelector: '.guided-tour-overview',
            headlineTranslateKey: 'tour.courseOverview.overviewMenu.headline',
            contentTranslateKey: 'tour.courseOverview.overviewMenu.content',
            highlightPadding: 10,
            orientation: Orientation.BOTTOM,
        }),
        new ImageTourStep({
            headlineTranslateKey: 'tour.courseOverview.welcome.headline',
            subHeadlineTranslateKey: 'tour.courseOverview.welcome.subHeadline',
            contentTranslateKey: 'tour.courseOverview.welcome.content',
            imageUrl: 'https://ase.in.tum.de/lehrstuhl_1/images/teaching/interactive/InteractiveLearning.png',
        }),
        new VideoTourStep({
            headlineTranslateKey: 'tour.courseExerciseOverview.installPrerequisites.sourceTreeSetup.headline',
            contentTranslateKey: 'tour.courseExerciseOverview.installPrerequisites.sourceTreeSetup.content',
            hintTranslateKey: 'tour.courseExerciseOverview.installPrerequisites.sourceTreeSetup.hint',
            videoUrl: 'tour.courseExerciseOverview.installPrerequisites.sourceTreeSetup.videoUrl',
        }),
        new ModelingTaskTourStep({
            highlightSelector: 'jhi-modeling-editor .guided-tour.modeling-editor .modeling-editor',
            headlineTranslateKey: 'tour.modelingExercise.executeTasks.headline',
            contentTranslateKey: 'tour.modelingExercise.executeTasks.content',
            highlightPadding: 5,
            orientation: Orientation.TOP,
            userInteractionEvent: UserInteractionEvent.MODELING,
            modelingTask: new GuidedTourModelingTask(personUML.name, 'tour.modelingExercise.executeTasks.personClass'),
        }),
        // ...
    ],
};
