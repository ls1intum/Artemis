/**
 * Created by muenchdo on 11/06/16.
 */
(function () {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .component('exerciseList', {
            bindings: {
                course: '<',
                filterByExerciseId: '<'
            },
            templateUrl: 'app/courses/exercises/exercise-list.html',
            controller: ExerciseListController
        });

    ExerciseListController.$inject = ['$sce', '$window', 'AlertService', 'CourseExercises', 'ExerciseParticipation', '$http'];

    function ExerciseListController($sce, $window, AlertService, CourseExercises, ExerciseParticipation, $http) {
        var vm = this;

        vm.clonePopover = {
            placement: 'left'
        };
        vm.loading = {};

        vm.$onInit = init;
        getRepositoryPassword().then(function (password) {
            vm.repositoryPassword = password;
        });

        vm.getClonePopoverTemplate = getClonePopoverTemplate;
        vm.goToBuildPlan = goToBuildPlan;
        vm.hasParticipation = hasParticipation;
        vm.start = start;

        function init() {
            CourseExercises.query({courseId: vm.course.id}).$promise.then(function (exercises) {

                if (vm.filterByExerciseId) {
                    exercises = _.filter(exercises, {id: vm.filterByExerciseId})
                }

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
            return 'sourcetree://cloneRepo?type=stash&cloneUrl=' + encodeURI(cloneUrl) + '&baseWebUrl=https://repobruegge.in.tum.de';
        }

        var trusted = {};

        function getClonePopoverTemplate(exercise) {
            var html = [
                '<div>',
                '<p>Clone your personal repository for this exercise:</p>',
                '<pre>', exercise.participation.repositoryUrl, '</pre>',
                vm.repositoryPassword ? '<p>Your password is: <code> ' + vm.repositoryPassword + ' </code><p>' : '',
                '<a class="btn btn-primary btn-sm" href="', buildSourceTreeUrl(exercise.participation.repositoryUrl), '">Clone in SourceTree</a>',
                ' <a href="http://www.sourcetreeapp.com" target="_blank">Atlassian SourceTree</a> is the free Git and Mercurial client for Windows or Mac. ',
                '</div>'
            ].join('');
            return trusted[html] || (trusted[html] = $sce.trustAsHtml(html));
        }

        function goToBuildPlan(exercise) {
            if (exercise.publishBuildPlanUrl) {
                var buildPlan = exercise.baseProjectKey + '-' + exercise.participation.student.login.replace(/[^a-zA-Z0-9]/g, "");
                $window.open('https://bamboobruegge.in.tum.de/browse/' + buildPlan.toUpperCase());
            }
        }

        function hasParticipation(exercise) {
            return !angular.equals({}, exercise.participation.toJSON());
        }

        function getRepositoryPassword() {
            return $http.get('api/account/password', {
                ignoreLoadingBar: true
            }).then(function (response) {
                return _.has(response, "data.password") && !_.isEmpty(response.data.password) ? response.data.password : null;
            }).catch(function () {
                return null;
            });
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
                    type: 'danger',
                    msg: 'Uh oh! Something went wrong... Please try again in a few seconds.',
                    timeout: 10000
                });
            }).finally(function () {
                vm.loading[exercise.id.toString()] = false;
            });
        }
    }
})();
