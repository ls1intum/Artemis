(function () {
    'use strict';

    angular
        .module('artemisApp')
        .component('instructorDashboard', {
            bindings: {
                courseId: '<',
                exerciseId: '<'
            },
            controller: InstructorDashboardController,
            templateUrl: 'app/instructor-dashboard/instructor-dashboard.html'
        });

    InstructorDashboardController.$inject = ['$window', '$filter', 'moment', '$uibModal', 'Exercise', 'ExerciseResults', 'Participation'];

    function InstructorDashboardController($window, $filter, moment, $uibModal, Exercise, ExerciseResults, Participation) {
        var vm = this;

        vm.showAllResults = false;
        vm.sortReverse = false;

        vm.$onInit = init;
        vm.durationString = durationString;
        vm.export = exportData;
        vm.goToBuildPlan = goToBuildPlan;
        vm.goToRepository = goToRepository;
        vm.refresh = getResults;
        vm.showDetails = showDetails;
        vm.sort = sort;
        vm.toggleShowAllResults = toggleShowAllResults;

        function init() {
            Exercise.get({id : vm.exerciseId}).$promise.then(function(exercise) {
                vm.exercise = exercise;
                getResults();
            });
        }

        function durationString(completionDate, initializationDate) {
            if (vm.exercise.type === 'quiz') {
                //TODO: distinguish between live mode and practice mode
                return $filter('amDifference')(completionDate, vm.exercise.releaseDate, 'minutes');
            }
            else {
                return $filter('amDifference')(completionDate, initializationDate, 'minutes');
            }
        }

        function exportData() {
            if (vm.sortedResults.length > 0) {
                var rows = [];
                vm.sortedResults.forEach(function (result, index) {
                    var studentName = result.participation.student.firstName;
                    rows.push(index === 0 ? 'data:text/csv;charset=utf-8,' + studentName : studentName);
                });
                var csvContent = rows.join('\n');
                var encodedUri = encodeURI(csvContent);
                var link = document.createElement('a');
                link.setAttribute('href', encodedUri);
                link.setAttribute('download', 'results.csv');
                document.body.appendChild(link); // Required for FF
                link.click();
            }
        }

        //TODO: only use rated results here for quizzes

        function getResults() {
            ExerciseResults.query({
                courseId: vm.courseId,
                exerciseId: vm.exerciseId,
                ratedOnly: vm.exercise.type === 'quiz'
            }).$promise.then(function(results) {
                results.forEach(function(result) {
                    result.participation.results = [result];
                });
                vm.allResults = results;
                filterResults()
            });
        }

        function goToBuildPlan(result) {
            Participation.buildPlanWebUrl({id: result.participation.id}).$promise.then(function (response) {
                $window.open(response.url);
            });
        }

        function goToRepository(result) {
            Participation.repositoryWebUrl({id: result.participation.id}).$promise.then(function (response) {
                $window.open(response.url);
            });
        }

        function showDetails(result) {
            $uibModal.open({
                size: 'lg',
                templateUrl: 'app/courses/results/result-detail.html',
                controller: ['$http', 'result', function ($http, result) {
                    var vm = this;
                    vm.$onInit = init;

                    function init() {
                        vm.loading = true;
                        $http.get('api/results/' + result.id + '/details', {
                            params: {
                                username: result.participation.student.login
                            }
                        }).then(function (response) {
                            vm.details = response.data;
                        }).finally(function () {
                            vm.loading = false;
                        });
                    }
                }],
                resolve: {
                    result: result
                },
                controllerAs: '$ctrl'
            });
        }

        function sort(item) {
            if (vm.sortColumn === 'completionDate') {
                return item.completionDate;
            } else if (vm.sortColumn === 'studentName') {
                return item.participation.student.firstName;
            } else if (vm.sortColumn === 'successful') {
                return item.successful;
            } else if (vm.sortColumn === 'submissionCount') {
                return item.submissionCount;
            } else if (vm.sortColumn === 'duration') {
                var completionDate = moment(item.completionDate);
                var initializationDate = moment(item.participation.initializationDate);
                return completionDate.diff(initializationDate, 'minutes');
            }
        }

        function toggleShowAllResults(newValue) {
            vm.showAllResults = newValue;
            filterResults()
        }

        function filterResults() {
            if (vm.showAllResults) {
                vm.results = vm.allResults
            }
            else {
                vm.results = vm.allResults.filter(result => result.successful == true);
            }
        }
    }
})();
