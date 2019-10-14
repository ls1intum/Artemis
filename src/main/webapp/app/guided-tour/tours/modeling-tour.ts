import { GuidedTour } from 'app/guided-tour/guided-tour.model';
import { ImageTourStep, TextTourStep } from 'app/guided-tour/guided-tour-step.model';
import { Orientation, UserInteractionEvent } from 'app/guided-tour/guided-tour.constants';

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
            headlineTranslateKey: 'tour.modelingExercise.addUmlElement.headline',
            contentTranslateKey: 'tour.modelingExercise.addUmlElement.description.content',
            imageUrl: '/../../../content/images/guided-tour-images/uml-add-class.gif',
        }),
        new TextTourStep({
            highlightSelector: 'jhi-modeling-editor .modeling-editor .modeling-editor',
            headlineTranslateKey: 'tour.modelingExercise.addUmlElement.headline',
            contentTranslateKey: 'tour.modelingExercise.addUmlElement.task.content',
            highlightPadding: 5,
            orientation: Orientation.TOP,
            userInteractionEvent: UserInteractionEvent.MODELING,
        }),
        new ImageTourStep({
            headlineTranslateKey: 'tour.modelingExercise.editUmlElement.headline',
            contentTranslateKey: 'tour.modelingExercise.editUmlElement.content',
            imageUrl: '/../../../content/images/guided-tour-images/uml-edit-class.gif',
        }),
        new ImageTourStep({
            headlineTranslateKey: 'tour.modelingExercise.createAssociation.headline',
            contentTranslateKey: 'tour.modelingExercise.createAssociation.description.content',
            imageUrl: '/../../../content/images/guided-tour-images/uml-create-association.gif',
        }),
        new TextTourStep({
            highlightSelector: 'jhi-modeling-editor .modeling-editor .modeling-editor',
            headlineTranslateKey: 'tour.modelingExercise.createAssociation.headline',
            contentTranslateKey: 'tour.modelingExercise.createAssociation.task.content',
            highlightPadding: 5,
            orientation: Orientation.TOP,
            userInteractionEvent: UserInteractionEvent.MODELING,
        }),
        new TextTourStep({
            highlightSelector: '.submission-buttons',
            headlineTranslateKey: 'tour.modelingExercise.saveAndSubmit.headline',
            contentTranslateKey: 'tour.modelingExercise.saveAndSubmit.content',
            highlightPadding: 10,
            orientation: Orientation.LEFT,
        }),
        new TextTourStep({
            highlightSelector: '.modeling-editor .btn-warning',
            headlineTranslateKey: 'tour.modelingExercise.help.headline',
            contentTranslateKey: 'tour.modelingExercise.help.content',
            highlightPadding: 10,
            orientation: Orientation.RIGHT,
        }),
    ],
};
