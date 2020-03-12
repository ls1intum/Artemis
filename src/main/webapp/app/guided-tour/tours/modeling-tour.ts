import { GuidedTour } from 'app/guided-tour/guided-tour.model';
import { ImageTourStep, ModelingTaskTourStep, TextTourStep, UserInterActionTourStep } from 'app/guided-tour/guided-tour-step.model';
import { Orientation, ResetParticipation, UserInteractionEvent } from 'app/guided-tour/guided-tour.constants';
import { associationUML, GuidedTourModelingTask, personUML, studentUML } from 'app/guided-tour/guided-tour-task.model';

export const modelingTour: GuidedTour = {
    settingsKey: 'modeling_tour',
    resetParticipation: ResetParticipation.EXERCISE_PARTICIPATION,
    steps: [
        new TextTourStep({
            headlineTranslateKey: 'tour.modelingExercise.editorArea.headline',
            contentTranslateKey: 'tour.modelingExercise.editorArea.content',
        }),
        new ImageTourStep({
            headlineTranslateKey: 'tour.modelingExercise.addEditUmlElement.headline',
            contentTranslateKey: 'tour.modelingExercise.addEditUmlElement.content',
            hintTranslateKey: 'tour.modelingExercise.addEditUmlElement.hint',
            imageUrl: '/../../../content/images/guided-tour-images/apollon-add-edit-element.gif',
        }),
        new ImageTourStep({
            headlineTranslateKey: 'tour.modelingExercise.createAssociation.headline',
            contentTranslateKey: 'tour.modelingExercise.createAssociation.content',
            hintTranslateKey: 'tour.modelingExercise.createAssociation.hint',
            imageUrl: '/../../../content/images/guided-tour-images/apollon-add-association.gif',
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
        new ModelingTaskTourStep({
            highlightSelector: 'jhi-modeling-editor .guided-tour.modeling-editor .modeling-editor',
            headlineTranslateKey: 'tour.modelingExercise.executeTasks.headline',
            contentTranslateKey: 'tour.modelingExercise.executeTasks.content',
            orientation: Orientation.TOP,
            userInteractionEvent: UserInteractionEvent.MODELING,
            modelingTask: new GuidedTourModelingTask(studentUML.name, 'tour.modelingExercise.executeTasks.studentClass'),
        }),
        new ModelingTaskTourStep({
            highlightSelector: 'jhi-modeling-editor .guided-tour.modeling-editor .modeling-editor',
            headlineTranslateKey: 'tour.modelingExercise.executeTasks.headline',
            contentTranslateKey: 'tour.modelingExercise.executeTasks.content',
            orientation: Orientation.TOP,
            userInteractionEvent: UserInteractionEvent.MODELING,
            modelingTask: new GuidedTourModelingTask(associationUML.name, 'tour.modelingExercise.executeTasks.association'),
        }),
        new UserInterActionTourStep({
            highlightSelector: 'jhi-modeling-submission .guided-tour.submission-button',
            headlineTranslateKey: 'tour.modelingExercise.submit.headline',
            contentTranslateKey: 'tour.modelingExercise.submit.content',
            hintTranslateKey: 'tour.modelingExercise.submit.hint',
            highlightPadding: 20,
            orientation: Orientation.LEFT,
            userInteractionEvent: UserInteractionEvent.CLICK,
            triggerNextStep: true,
        }),
    ],
};
