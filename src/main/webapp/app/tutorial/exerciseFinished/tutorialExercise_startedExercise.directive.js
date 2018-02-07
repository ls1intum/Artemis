angular
    .module('artemisApp')
    .component('exerciseStarted', {
        bindings: {
            course: '<',
            filterByExerciseId: '<'
        },
        templateUrl: 'app/tutorial/exerciseFinished/tutorialExercise_caseStarted.html',
        controller: 'ExerciseListController'
    });

