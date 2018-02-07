angular
    .module('artemisApp')
    .component('tutorialExerciseList', {
        bindings: {
            course: '<',
            filterByExerciseId: '<'
        },
        templateUrl: 'app/tutorial/exercises/tutorialExercises.html',
        controller: 'ExerciseListController'
    });

