import { GuidedTour } from 'app/guided-tour/guided-tour.model';
import { ImageTourStep, TextTourStep } from 'app/guided-tour/guided-tour-step.model';
import { Orientation, UserInteractionEvent } from 'app/guided-tour/guided-tour.constants';
import { associationUML, GuidedTourTask, personUML, studentUML } from 'app/guided-tour/guided-tour-task.model';

export const modelingTour: GuidedTour = {
    courseShortName: 'artemistutorial',
    exerciseShortName: 'Modeling',
    settingsKey: 'modeling_tour',
    steps: [
        new TextTourStep({
            headlineTranslateKey: 'tour.modelingExercise.editorArea.headline',
            contentTranslateKey: 'tour.modelingExercise.editorArea.content',
        }),
        new ImageTourStep({
            headlineTranslateKey: 'tour.modelingExercise.addEditUmlElement.headline',
            contentTranslateKey: 'tour.modelingExercise.addEditUmlElement.content',
            imageUrl: '/../../../content/images/guided-tour-images/apollon-add-edit-element.gif',
        }),
        new ImageTourStep({
            headlineTranslateKey: 'tour.modelingExercise.createAssociation.headline',
            contentTranslateKey: 'tour.modelingExercise.createAssociation.content',
            imageUrl: '/../../../content/images/guided-tour-images/apollon-add-association.gif',
        }),
        new TextTourStep({
            highlightSelector: 'jhi-modeling-editor .modeling-editor .modeling-editor',
            headlineTranslateKey: 'tour.modelingExercise.executeTasks.headline',
            contentTranslateKey: 'tour.modelingExercise.executeTasks.content',
            highlightPadding: 5,
            orientation: Orientation.TOP,
            userInteractionEvent: UserInteractionEvent.MODELING,
            task: new GuidedTourTask(personUML.name, 'tour.modelingExercise.executeTasks.personClass'),
        }),
        new TextTourStep({
            highlightSelector: 'jhi-modeling-editor .modeling-editor .modeling-editor',
            headlineTranslateKey: 'tour.modelingExercise.executeTasks.headline',
            contentTranslateKey: 'tour.modelingExercise.executeTasks.content',
            orientation: Orientation.TOP,
            userInteractionEvent: UserInteractionEvent.MODELING,
            task: new GuidedTourTask(studentUML.name, 'tour.modelingExercise.executeTasks.studentClass'),
        }),
        new TextTourStep({
            highlightSelector: 'jhi-modeling-editor .modeling-editor .modeling-editor',
            headlineTranslateKey: 'tour.modelingExercise.executeTasks.headline',
            contentTranslateKey: 'tour.modelingExercise.executeTasks.content',
            orientation: Orientation.TOP,
            userInteractionEvent: UserInteractionEvent.MODELING,
            task: new GuidedTourTask(associationUML.name, 'tour.modelingExercise.executeTasks.association'),
        }),
        new TextTourStep({
            headlineTranslateKey: 'tour.modelingExercise.finishedTasks.headline',
            contentTranslateKey: 'tour.modelingExercise.finishedTasks.content',
        }),
    ],
};
