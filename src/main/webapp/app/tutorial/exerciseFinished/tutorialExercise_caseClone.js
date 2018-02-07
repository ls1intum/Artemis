angular
    .module('artemisApp')
    .component('exerciseClone', {
        bindings: {
            course: '<',
            filterByExerciseId: '<'
        },
        templateUrl: 'app/tutorial/exerciseFinished/tutorialExercise_caseClone.html',
        controller: 'ExerciseListController'
    });

