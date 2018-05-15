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

    InstructorDashboardController.$inject = ['$window', '$filter', 'moment', '$uibModal', 'Exercise', 'ExerciseResults', 'Participation', 'Repository', '$sce'];

    function InstructorDashboardController($window, $filter, moment, $uibModal, Exercise, ExerciseResults, Participation, Repository, $sce) {
        var vm = this;

        vm.showAllResults = 'all';
        vm.sortReverse = false;

        vm.$onInit = init;
        vm.durationString = durationString;
        vm.exportResults = exportResults;
        vm.exportNames = exportNames;
        vm.goToBuildPlan = goToBuildPlan;
        vm.goToRepository = goToRepository;
        vm.refresh = getResults;
        vm.showDetails = showDetails;
        vm.sort = sort;
        vm.toggleShowAllResults = toggleShowAllResults;
        vm.resultString = resultString;
        vm.getTextColorClass = getTextColorClass;
        vm.getResultIconClass = getResultIconClass;
        vm.hasFeedback = hasFeedback;

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

        function exportResults() {
            if (vm.sortedResults.length > 0) {
                var rows = [];
                vm.sortedResults.forEach(function (result, index) {
                    var studentName = result.participation.student.firstName;
                    var studentId = result.participation.student.login;
                    var score = result.score;
                    if (index === 0) {
                        if (vm.exercise.type === 'quiz') {
                            rows.push('data:text/csv;charset=utf-8,Name, Username, Score')
                        }
                        else {
                            rows.push('data:text/csv;charset=utf-8,Name, Username, Score, Repo Link')
                        }
                    }
                    if (vm.exercise.type === 'quiz') {
                        rows.push(studentName + ', ' + studentId + ', ' + score);
                    }
                    else {
                        var repoLink = result.participation.repositoryUrl;
                        rows.push(studentName + ', ' + studentId + ', ' + score + ', ' + repoLink);
                    }
                });
                var csvContent = rows.join('\n');
                var encodedUri = encodeURI(csvContent);
                var link = document.createElement('a');
                link.setAttribute('href', encodedUri);
                link.setAttribute('download', 'results-scores.csv');
                document.body.appendChild(link); // Required for FF
                link.click();
            }
        }

        function exportNames() {
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
                link.setAttribute('download', 'results-names.csv');
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
                            if (vm.details.length == 0) {
                                Repository.buildlogs({
                                    participationId: result.participation.id
                                }, function (buildLogs) {
                                    _.forEach(buildLogs, function (buildLog) {
                                        buildLog.log = $sce.trustAsHtml(buildLog.log);
                                    });
                                    vm.buildLogs = buildLogs;
                                    vm.loading = false;
                                });
                            } else {
                                vm.loading = false;
                            }
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
            vm.results = {}

            if (vm.showAllResults == 'successful') {
                vm.results = vm.allResults.filter(function(result) {
                    return result.successful == true;
                });
            }
            else if (vm.showAllResults == 'unsuccessful') {
                vm.results = vm.allResults.filter(function(result) {
                    return result.successful == false;
                });
            }
            else {
                vm.results = vm.allResults
            }
        }


        //helper functions copied from result.component.js //TODO: think about a better solution

        function resultString(result) {
            if (result.resultString === 'No tests found') {
                return 'Build failed';
            } else {
                return result.resultString;
            }
        }

        function hasFeedback(result) {
            if (resultString(result) === 'Build failed') {
                return true;
            }
            if (result.hasFeedback === null) {
                return false;
            }
            else return result.hasFeedback;
        }

        /**
         * Get the css class for the entire text as a string
         *
         * @return {string} the css class
         */
        function getTextColorClass(result) {
            if (result.score == null) {
                if (result.successful) {
                    return "text-success";
                } else {
                    return "text-danger";
                }
            } else {
                if (result.score > 80) {
                    return "text-success";
                } else if (result.score > 40) {
                    return "result-orange";
                } else {
                    return "text-danger";
                }
            }
        }

        //TODO think about a better color scheme

        /**
         * Get the css class for the result icon as a string
         *
         * @return {string} the css class
         */
        function getResultIconClass(result) {
            if (result.score == null) {
                if (result.successful) {
                    return "fa-check-circle-o";
                } else {
                    return "fa-times-circle-o";
                }
            } else {
                if (result.score > 80) {
                    return "fa-check-circle-o";
                } else {
                    return "fa-times-circle-o";
                }
            }
        }


    }
})();
