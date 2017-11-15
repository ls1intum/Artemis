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

    ExerciseListController.$inject = ['$sce', '$window', 'AlertService', 'CourseExercises', 'Participation', 'ExerciseParticipation', '$http', '$location', 'Principal', '$rootScope'];

    function ExerciseListController($sce, $window, AlertService, CourseExercises, Participation, ExerciseParticipation, $http, $location, Principal, $rootScope) {
        var vm = this;

        vm.clonePopover = {
            placement: 'left'
        };
        vm.loading = {};

        vm.$onInit = init;

        vm.getClonePopoverTemplate = getClonePopoverTemplate;
        vm.goToBuildPlan = goToBuildPlan;
        vm.participationStatus = participationStatus;
        vm.start = start;
        vm.resume = resume;
        vm.now = Date.now();
        vm.numOfOverdueExercises = 0;
        vm.showOverdueExercises = false;

        function init() {

            if ($location.search().welcome) {
                showWelcomeAlert();
            }

            getRepositoryPassword().then(function (password) {
                vm.repositoryPassword = password;
            });

            CourseExercises.query({
                courseId: vm.course.id,
                withLtiOutcomeUrlExisting: true
            }).$promise.then(function (exercises) {

                if (vm.filterByExerciseId) {
                    exercises = _.filter(exercises, {id: vm.filterByExerciseId})
                }

                vm.numOfOverdueExercises = _.filter(exercises, function (exercise) {
                    return !isNotOverdue(exercise);
                }).length;

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
                '<pre style="max-width: 550px;">', exercise.participation.repositoryUrl, '</pre>',
                vm.repositoryPassword ? '<p>Your password is: <code class="password">' + vm.repositoryPassword + '</code> (hover to show)<p>' : '',
                '<a class="btn btn-primary btn-sm" href="', buildSourceTreeUrl(exercise.participation.repositoryUrl), '">Clone in SourceTree</a>',
                ' <a href="http://www.sourcetreeapp.com" target="_blank">Atlassian SourceTree</a> is the free Git client for Windows or Mac. ',
                '</div>'
            ].join('');
            return trusted[html] || (trusted[html] = $sce.trustAsHtml(html));
        }

        function goToBuildPlan(exercise) {
            Participation.buildPlanWebUrl({id: exercise.participation.id}).$promise.then(function (response) {
                $window.open(response.url);
            });
        }

        function participationStatus(exercise) {
            if (exercise.type && exercise.type === "quiz") {
                if (angular.equals({}, exercise.participation)) {
                    return "quiz-uninitialized";
                } else if (exercise.participation.initializationState === "INITIALIZED" && moment(exercise.dueDate).isAfter(moment())) {
                    return "quiz-active";
                } else {
                    return "quiz-finished"
                }
            }
            if (angular.equals({}, exercise.participation)) {
                return "uninitialized";
            } else if (exercise.participation.initializationState === "INITIALIZED") {
                return "initialized";
            }
            return "inactive";
        }

        function isNotOverdue(exercise) {
            return vm.showOverdueExercises || _.isEmpty(exercise.dueDate) || vm.now <= Date.parse(exercise.dueDate);
        }

        vm.isNotOverdue = isNotOverdue;

        function getRepositoryPassword() {
            return $http.get('api/account/password', {
                ignoreLoadingBar: true
            }).then(function (response) {
                return _.has(response, "data.password") && !_.isEmpty(response.data.password) ? response.data.password : null;
            }).catch(function () {
                return null;
            });
        }

        function showWelcomeAlert() {
            AlertService.add({
                type: 'info',
                msg: '<strong>Welcome to ArTEMiS!</strong> We have automatically created an account for you. Click the <i>Start Exercise</i> button to get started!'
            });
        }

        function start(exercise) {
            vm.loading[exercise.id.toString()] = true;

            if (exercise.type && exercise.type === "quiz") {
                // start the quiz
                $location.url("/quiz/" + exercise.id);
                return;
            }

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
            }).catch(function (e) {
                console.log(e);
                AlertService.add({
                    type: 'danger',
                    msg: '<strong>Uh oh! Something went wrong... Please try again in a few seconds.</strong> If this problem persists, please <a href="mailto:' + $rootScope.CONTACT_EMAIL + '?subject=Exercise%20Application%20Error%20Report&body=' + e.data.description + '">send us an error report</a>.',
                    timeout: 30000
                });
            }).finally(function () {
                vm.loading[exercise.id.toString()] = false;
            });
        }

        function resume(exercise) {
            vm.loading[exercise.id] = true;
            exercise.$resume({
                courseId: exercise.course.id,
                exerciseId: exercise.id
            }).catch(function (errorResponse) {
                alert(errorResponse.data.status + " " + errorResponse.data.detail);
            }).finally(function () {
                vm.loading[exercise.id] = false;
            });
        }

        function toggleShowOverdueExercises() {
            vm.showOverdueExercises = true;
        }

        vm.toggleShowOverdueExercises = toggleShowOverdueExercises;

    }
})();
