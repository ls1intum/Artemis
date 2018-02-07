angular
    .module('artemisApp')
    .component('exerciseFinished', {
        bindings: {
            course: '<',
            filterByExerciseId: '<'
        },
        templateUrl: 'app/tutorial/exerciseFinished/tutorialExercise_caseFinished.html',
        controller: 'ExerciseListController'
    });

