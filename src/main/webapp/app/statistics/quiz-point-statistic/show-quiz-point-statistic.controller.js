(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('ShowQuizPointStatisticController', ShowQuizPointStatisticController);

    ShowQuizPointStatisticController.$inject = ['$translate', '$scope', '$state', 'Principal', 'JhiWebsocketService', 'QuizPointStatistic', 'QuizPointStatisticForStudent', 'QuizExercise', 'QuizExerciseForStudent'];

    function ShowQuizPointStatisticController($translate, $scope, $state, Principal, JhiWebsocketService, QuizPointStatistic, QuizPointStatisticForStudent, QuizExercise, QuizExerciseForStudent) {
        var vm = this;

        // Variables for the chart:
        var label;
        var ratedData;
        var unratedData;
        var backgroundColor;

        vm.switchRated = switchRated;
        vm.previousStatistic = previousStatistic;
        vm.releaseStatistics = releaseStatistics;
        vm.releaseButtonDisabled = releaseButtonDisabled;

        vm.rated = true;
        vm.$onInit = init;

        /**
         * loads quizExercise with the quizPointStatistic from server and sets up socket connections
         */
        function init() {
            // use different REST-call if the User is a Student
            if(Principal.hasAnyAuthority(['ROLE_ADMIN', 'ROLE_TA'])) {
                QuizExercise.get({id: _.get($state,"params.quizId")}).$promise.then(loadQuizSuccess);
            }
            else{
                QuizExerciseForStudent.get({id: _.get($state,"params.quizId")}).$promise.then(loadQuizSuccess);
            }

            //subscribe websocket for new statistical data
            var websocketChannelForData = '/topic/statistic/'+ _.get($state,"params.quizId");
            JhiWebsocketService.subscribe(websocketChannelForData);

            //subscribe websocket which notifies the user if the release status was changed
            var websocketChannelForReleaseState = websocketChannelForData + '/release';
            JhiWebsocketService.subscribe(websocketChannelForReleaseState);

            // ask for new Data if the websocket for new statistical data was notified
            JhiWebsocketService.receive(websocketChannelForData).then(null, null, function(notify) {
                if(Principal.hasAnyAuthority(['ROLE_ADMIN', 'ROLE_TA'])) {
                    QuizPointStatistic.get({id: vm.quizPointStatistic.id}).$promise.then(loadNewData);
                }
                else{
                    QuizPointStatisticForStudent.get({id: vm.quizPointStatistic.id}).$promise.then(loadNewData);
                }

            });
            // refresh release information
            JhiWebsocketService.receive(websocketChannelForReleaseState).then(null, null, function(payload) {
                vm.quizExercise.quizPointStatistic.released = payload;
                // send students back to courses if the statistic was revoked
                if(!Principal.hasAnyAuthority(['ROLE_ADMIN', 'ROLE_TA']) && !payload) {
                    $state.go('courses');
                }
            });

            $scope.$on('$destroy', function() {
                JhiWebsocketService.unsubscribe(websocketChannelForData);
                JhiWebsocketService.unsubscribe(websocketChannelForReleaseState);
            });

            console.log(barChartData);
            // add Axes-labels based on selected language
            $translate('showStatistic.quizPointStatistic.xAxes').then(function (xLabel) {
                window.myChart.options.scales.xAxes[0].scaleLabel.labelString = xLabel;
            });
            $translate('showStatistic.quizPointStatistic.yAxes').then(function (yLabel) {
                window.myChart.options.scales.yAxes[0].scaleLabel.labelString = yLabel;
            });
        }

        /**
         * load the new quizPointStatistic from the server if the Websocket has been notified
         *
         * @param {QuizPointStatistic} statistic: the new quizPointStatistic from the server with the new Data.
         */
        function loadNewData(statistic) {
            // if the Student finds a way to the Website, while the Statistic is not released -> the Student will be send back to Courses
            if( (!Principal.hasAnyAuthority(['ROLE_ADMIN', 'ROLE_TA'])) && statistic.released == false) {
                $state.go('courses');
            }
            vm.quizPointStatistic = statistic;
            loadData();
        }

        /**
         * This functions loads the Quiz, which is necessary to build the Web-Template
         *
         * @param {QuizExercise} quiz: the quizExercise, which the this quiz-point-statistic presents.
         */
        function loadQuizSuccess(quiz) {
            // if the Student finds a way to the Website, while the Statistic is not released -> the Student will be send back to Courses
            if( (!Principal.hasAnyAuthority(['ROLE_ADMIN', 'ROLE_TA'])) && quiz.quizPointStatistic.released == false) {
                $state.go('courses');
            }
            vm.quizExercise = quiz;
            vm.quizPointStatistic = vm.quizExercise.quizPointStatistic;
            vm.maxScore = calculateMaxScore();

            loadData();

        }

        /**
         * calculate the maximal  possible Score for the quiz
         *
         * @return (int): sum over the Scores of all questions
         */
        function calculateMaxScore() {

            var result = 0;

            vm.quizExercise.questions.forEach(function(question) {
                result = result + question.score;
            });
            return result;
        }

        /**
         * load the Data from the Json-entity to the chart: myChart
         */
        function loadData() {

            // reset old data
            label = [];
            backgroundColor = [];
            ratedData = [];
            unratedData = [];

            //set data based on the pointCounters
            vm.quizPointStatistic.pointCounters.forEach(function (pointCounter) {
                label.push(pointCounter.points);
                ratedData.push(pointCounter.ratedCounter);
                unratedData.push(pointCounter.unRatedCounter);
                backgroundColor.push("#428bca");
            });
            // order the bars ascending on points
            order();

            barChartData.labels = label;

            // load data into the chart
            // if vm.rated == true  -> load the rated data
            if (vm.rated) {
                vm.participants = vm.quizPointStatistic.participantsRated;
                barChartData.participants = vm.quizPointStatistic.participantsRated;
                barChartData.datasets.forEach(function (dataset) {
                    dataset.data = ratedData;
                    dataset.backgroundColor = backgroundColor;
                });
            }
            //else: load the unrated data
            else {
                vm.participants = vm.quizPointStatistic.participantsUnrated;
                barChartData.participants = vm.quizPointStatistic.participantsUnrated;
                barChartData.datasets.forEach(function (dataset) {
                    dataset.data = unratedData;
                    dataset.backgroundColor = backgroundColor;
                });
            }
            window.myChart.update();

        }

        /**
         * switch between showing and hiding the solution in the chart
         *  1. change the amount of  participants
         *  2. change the bar-Data
         */
        function switchRated() {
            if(vm.rated) {
                //load unrated Data
                barChartData.datasets.forEach(function (dataset) {
                    dataset.data = unratedData;
                });
                vm.participants = vm.quizPointStatistic.participantsUnrated;
                barChartData.participants = vm.quizPointStatistic.participantsUnrated;
                vm.rated = false;
            }
            else{
                //load rated Data
                barChartData.datasets.forEach(function (dataset) {
                    dataset.data = ratedData;
                });
                vm.participants = vm.quizPointStatistic.participantsRated;
                barChartData.participants = vm.quizPointStatistic.participantsRated;
                vm.rated = true;
            }
            window.myChart.update();
        }

        /**
         * order the data and the associated Labels, so that they are ascending (BubbleSort)
         */
        function order() {
            var old = [];
            while (old.toString() !== label.toString()) {
                old = label.slice();
                for(var i = 0; i < label.length-1; i ++) {
                    if(label[i] > label[i+1]) {
                        // switch Labels
                        var temp = label[i];
                        label[i] = label[i+1];
                        label[i+1] = temp;
                        // switch rated Data
                        temp = ratedData[i];
                        ratedData[i] = ratedData[i+1];
                        ratedData[i+1] = temp;
                        // switch unrated Data
                        temp = unratedData[i];
                        unratedData[i] = unratedData[i+1];
                        unratedData[i+1] = temp;
                    }
                }
            }
        }

        /**
         * got to the Template with the previous Statistic -> the last QuestionStatistic
         * if there is no QuestionStatistic -> go to QuizStatistic
         */
        function previousStatistic() {
            if(vm.quizExercise.questions === null || vm.quizExercise.questions.length === 0) {
                $state.go('quiz-statistic-chart',{quizId: vm.quizExercise.id});
            }
            else{
                $state.go('multiple-choice-question-statistic-chart', {quizId: vm.quizExercise.id, questionId: vm.quizExercise.questions[vm.quizExercise.questions.length -1].id});
            }
        }

        /**
         * release of revoke the all statistics of the quizExercise
         *
         * @param {boolean} released: true to release, false to revoke
         */
        function releaseStatistics(released) {
            if (released === vm.quizExercise.quizPointStatistic.released ) {
                return;
            }
            // check if it's allowed to release the statistics, if not send alert and do nothing
            if (released && releaseButtonDisabled()) {
                alert("Quiz noch nicht beendet!");
                return;
            }
            if (vm.quizExercise.id) {
                vm.quizExercise.quizPointStatistic.released = released;
                for (var i = 0; i < vm.quizExercise.questions.length; i++) {
                    vm.quizExercise.questions[i].questionStatistic.released = released;
                }
                QuizExercise.update(vm.quizExercise);
            }
        }

        /**
         * check if it's allowed to release the Statistic (allowed if the quiz is finished)
         * @returns {boolean} true if it's allowed, false if not
         */
        function releaseButtonDisabled() {
            if (vm.quizExercise != null) {
                return (!vm.quizExercise.isPlannedToStart || moment().isBefore(vm.quizExercise.dueDate));
            }else{
                return true;
            }
        }

    }
})();
