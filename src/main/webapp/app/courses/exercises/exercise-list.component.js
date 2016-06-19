/**
 * Created by muenchdo on 11/06/16.
 */
(function () {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .component('exerciseList', {
            bindings: {
                course: '<'
            },
            templateUrl: 'app/courses/exercises/exercise-list.html',
            controller: ExerciseListController
        });

    ExerciseListController.$inject = ['$sce', 'AlertService', 'CourseExercises', 'ExerciseParticipation'];

    function ExerciseListController($sce, AlertService, CourseExercises, ExerciseParticipation) {
        var vm = this;

        vm.clonePopover = {
            placement: 'left'
        };
        vm.loading = {};

        vm.$onInit = init;
        vm.getClonePopoverTemplate = getClonePopoverTemplate;
        vm.hasParticipation = hasParticipation;
        vm.start = start;

        function init() {
            CourseExercises.query({courseId: vm.course.id}).$promise.then(function (exercises) {
                angular.forEach(exercises, function (exercise) {
                    exercise['participation'] = ExerciseParticipation.get({
                        courseId: exercise.course.id,
                        exerciseId: exercise.id
                    });
                });
                vm.exercises = exercises;
            });
        }

        function buildSourceTreeUrl(cloneUrl) {
            // sourcetree://cloneRepo?type=stash&cloneUrl=https%3A%2F%2Fga56hur%40repobruegge.in.tum.de%2Fscm%2Fmadm%2Fexercise-application.git
            return 'sourcetree://cloneRepo?type=stash&cloneUrl=' + encodeURI(cloneUrl);
        }

        var trusted = {};

        function getClonePopoverTemplate(exercise) {
            var html = [
                '<div>',
                '<p>Clone your personal repository for this exercise:</p>',
                '<pre>', exercise.participation.cloneUrl, '</pre>',
                '<a class="btn btn-primary btn-sm" href="', buildSourceTreeUrl(exercise.participation.cloneUrl),'">Clone in SourceTree</a>',
                ' <a href="http://www.sourcetreeapp.com" target="_blank">Atlassian SourceTree</a> is the free Git and Mercurial client for Windows or Mac.',
                '</div>'
            ].join('');
            return trusted[html] || (trusted[html] = $sce.trustAsHtml(html));
        }

        function hasParticipation(exercise) {
            return !angular.equals({}, exercise.participation.toJSON());
        }

        function start(exercise) {
            vm.loading[exercise.id.toString()] = true;
            exercise.$start({
                courseId: exercise.course.id,
                exerciseId: exercise.id
            }).then(function (returnedExercise) {
                exercise['participation'] = returnedExercise.participation;
                exercise['participation'].toJSON = exercise.toJSON;
                AlertService.add({
                    type: 'success',
                    msg: 'Your personal repository has been set up. Click the <i>Clone repository</i> button to get started!',
                    timeout: 10000
                });
            }).catch(function () {
                AlertService.add({
                    type: 'error',
                    msg: 'Uh oh! Something went wrong... Please try again in a few seconds.',
                    timeout: 10000
                });
            }).finally(function () {
                vm.loading[exercise.id.toString()] = false;
            });
        }
    }
})();
