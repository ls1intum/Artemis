/**
 * Created by muenchdo on 11/06/16.
 */
(function () {
    'use strict';

    angular
        .module('artemisApp')
        .component('exerciseList', {
            bindings: {
                course: '<',
                filterByExerciseId: '<'
            },
            templateUrl: 'app/courses/exercises/exercise-list.html',
            controller: ExerciseListController
        });
})();
