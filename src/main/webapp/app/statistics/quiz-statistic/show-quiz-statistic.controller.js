(function () {
    'use strict';

    angular
        .module('artemisApp')
        .controller('ShowQuizStatisticController', ShowQuizStatisticController);

    ShowQuizStatisticController.$inject = ['$translate', '$scope', '$state', 'Principal', 'JhiWebsocketService', 'QuizExercise', 'QuizExerciseForStudent', 'QuizStatisticService'];

    function ShowQuizStatisticController($translate, $scope, $state, Principal, JhiWebsocketService, QuizExercise, QuizExerciseForStudent, QuizStatisticService) {

        var vm = this;

        // Variables for the chart:
        vm.labels = [];
        vm.data = [];
        vm.colors = [];

        var label;
        var ratedData;
        var unratedData;
        var backgroundColor;
        var ratedAverage;
        var unratedAverage;

        vm.switchRated = switchRated;
        vm.nextStatistic = nextStatistic;
        vm.releaseStatistics = releaseStatistics;
        vm.releaseButtonDisabled = releaseButtonDisabled;

        var maxScore;

        vm.rated = true;
        vm.$onInit = init;

        /**
         * loads quizExercise with all multipleChoiceQuestionStatistics
         * from server and sets up socket connections
         */
        function init() {
            // use different REST-call if the User is a Student
            if (Principal.hasAnyAuthority(['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'])) {
                QuizExercise.get({id: _.get($state, "params.quizId")})
                    .$promise.then(loadQuizSuccess);
            }
            else {
                QuizExerciseForStudent.get({id: _.get($state, "params.quizId")})
                    .$promise.then(loadQuizSuccess);
            }

            //subscribe websocket for new statistical data
            var websocketChannelForData = '/topic/statistic/' + _.get($state, "params.quizId");
            JhiWebsocketService.subscribe(websocketChannelForData);

            //subscribe websocket which notifies the user if the release status was changed
            var websocketChannelForReleaseState = websocketChannelForData + '/release';
            JhiWebsocketService.subscribe(websocketChannelForReleaseState);

            // ask for new Data if the websocket for new statistical data was notified
            JhiWebsocketService.receive(websocketChannelForData)
                .then(null, null, function (quiz) {

                    loadQuizSuccess(quiz);
                });
            // refresh release information
            JhiWebsocketService.receive(websocketChannelForReleaseState)
                .then(null, null, function (payload) {
                    vm.quizExercise.quizPointStatistic.released = payload;
                    // send students back to courses if the statistic was revoked
                    if (!Principal.hasAnyAuthority(['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'])
                        && !payload) {
                        $state.go('courses');
                    }
                });

            $scope.$on('$destroy', function () {
                JhiWebsocketService.unsubscribe(websocketChannelForData);
                JhiWebsocketService.unsubscribe(websocketChannelForReleaseState);
            });

            // add Axes-labels based on selected language
            $translate('showStatistic.quizStatistic.xAxes').then(function (xLabel) {
                vm.options.scales.xAxes[0].scaleLabel.labelString = xLabel;
            });
            $translate('showStatistic.quizStatistic.yAxes').then(function (yLabel) {
                vm.options.scales.yAxes[0].scaleLabel.labelString = yLabel;
            });
        }

        /**
         * This functions loads the Quiz, which is necessary to build the Web-Template
         * And it loads the new Data if the Websocket has been notified
         *
         * @param {QuizExercise} quiz: the quizExercise, which the this quiz-statistic presents.
         */
        function loadQuizSuccess(quiz) {
            // if the Student finds a way to the Website, while the Statistic is not released
            //      -> the Student will be send back to Courses
            if ((!Principal.hasAnyAuthority(['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA']))
                && !quiz.quizPointStatistic.released) {
                $state.go('courses');
            }
            vm.quizExercise = quiz;
            maxScore = calculateMaxScore();
            loadData();
        }

        /**
         * calculate the maximal  possible Score for the quiz
         *
         * @return (int): sum over the Scores of all questions
         */
        function calculateMaxScore() {

            var result = 0;

            vm.quizExercise.questions.forEach(function (question) {
                result = result + question.score;
            });
            return result;
        }

        /**
         * load the Data from the Json-entity to the chart: myChart
         */
        function loadData() {

            // reset old data
            label = new Array(vm.quizExercise.questions.length);
            backgroundColor = [];
            ratedData = new Array(vm.quizExercise.questions.length);
            unratedData = new Array(vm.quizExercise.questions.length);
            ratedAverage = 0;
            unratedAverage = 0;

            //set data based on the CorrectCounters in the QuestionStatistics
            vm.quizExercise.questions.forEach(function (question, i) {

                label [i] = (i + 1 + ".");
                backgroundColor.push(
                    {
                        backgroundColor: "#5bc0de",
                        borderColor: "#5bc0de",
                        pointBackgroundColor: "#5bc0de",
                        pointBorderColor: "#5bc0de"
                    });
                ratedData[i] = question.questionStatistic.ratedCorrectCounter;
                unratedData[i] = question.questionStatistic.unRatedCorrectCounter;
                ratedAverage = ratedAverage + (question.questionStatistic.ratedCorrectCounter * question.score);
                unratedAverage = unratedAverage + (question.questionStatistic.unRatedCorrectCounter * question.score);

                if (question.invalid) {
                    backgroundColor[i] = (
                        {
                            backgroundColor: "#949494",
                            borderColor: "#949494",
                            pointBackgroundColor: "#949494",
                            pointBorderColor: "#949494"
                        });
                }
            });

            //add data for the last bar (Average)
            backgroundColor.push(
                {
                    backgroundColor: "#1e3368",
                    borderColor: "#1e3368",
                    pointBackgroundColor: "#1e3368",
                    pointBorderColor: "#1e3368"
                });
            ratedData.push(ratedAverage / maxScore);
            unratedData.push(unratedAverage / maxScore);

            // load data into the chart
            vm.labels = label;
            vm.colors = backgroundColor;

            //add Text for last label based on the language
            $translate('showStatistic.quizStatistic.average').then(function (lastLabel) {
                label.push(lastLabel);
            });

            loadDataInDiagram();
        }

        /**
         * check if the rated or unrated
         * load the rated or unrated data into the diagram
         */
        function loadDataInDiagram() {
            if (vm.rated) {
                vm.participants = vm.quizExercise.quizPointStatistic.participantsRated;
                vm.data = ratedData;
            }
            // else: load the unrated data
            else {
                vm.participants = vm.quizExercise.quizPointStatistic.participantsUnrated;
                vm.data = unratedData;
            }
        }

        /**
         * switch between showing and hiding the solution in the chart
         *  1. change the amount of  participants
         *  2. change the bar-Data
         */
        function switchRated() {
            vm.rated = !vm.rated;
            loadDataInDiagram();
        }

        /**
         * got to the Template with the next Statistic -> the first QuestionStatistic
         * if there is no QuestionStatistic -> go to QuizPointStatistic
         */
        function nextStatistic() {
            if (vm.quizExercise.questions === null || vm.quizExercise.questions.length === 0) {
                $state.go('quiz-point-statistic-chart', {quizId: vm.quizExercise.id});
            }
            else {
                if (vm.quizExercise.questions[0].type === "multiple-choice") {
                    $state.go('multiple-choice-question-statistic-chart', {
                        quizId: vm.quizExercise.id,
                        questionId: vm.quizExercise.questions[0].id
                    });
                }
                if (vm.quizExercise.questions[0].type === "drag-and-drop") {
                    $state.go('drag-and-drop-question-statistic-chart', {
                        quizId: vm.quizExercise.id,
                        questionId: vm.quizExercise.questions[0].id
                    });
                }
            }
        }

        /**
         * release of revoke all statistics of the quizExercise
         *
         * @param {boolean} released: true to release, false to revoke
         */
        function releaseStatistics(released) {
            QuizStatisticService.releaseStatistics(released, vm.quizExercise);
        }

        /**
         * check if it's allowed to release the Statistic (allowed if the quiz is finished)
         * @returns {boolean} true if it's allowed, false if not
         */
        function releaseButtonDisabled() {
            QuizStatisticService.releaseButtonDisabled(vm.quizExercise);
        }

        // options for chart in chart.js style
        vm.options = {
            layout: {
                padding: {
                    left: 0,
                    right: 0,
                    top: 0,
                    bottom: 30
                }
            },
            legend: {
                display: false
            },
            title: {
                display: false,
                text: "",
                position: "top",
                fontSize: "16",
                padding: 20
            },
            tooltips: {
                enabled: false
            },
            scales: {
                yAxes: [{
                    scaleLabel: {
                        labelString: '',
                        display: true
                    },
                    ticks: {
                        beginAtZero: true
                    }
                }],
                xAxes: [{
                    scaleLabel: {
                        labelString: '',
                        display: true
                    }
                }]
            },
            hover: {animationDuration: 0},
            //add numbers on top of the bars
            animation: {
                duration: 500,
                onComplete: function () {
                    var chartInstance = this.chart,
                        ctx = chartInstance.ctx;
                    var fontSize = 12;
                    var fontStyle = 'normal';
                    var fontFamily = 'Calibri';
                    ctx.font = Chart.helpers.fontString(fontSize, fontStyle, fontFamily);
                    ctx.textAlign = 'center';
                    ctx.textBaseline = 'middle';

                    this.data.datasets.forEach(function (dataset, i) {
                        var meta = chartInstance.controller.getDatasetMeta(i);
                        meta.data.forEach(function (bar, index) {
                            var data = (Math.round(dataset.data[index] * 100) / 100);
                            var dataPercentage = (Math.round((dataset.data[index] / vm.participants) * 1000) / 10);

                            var position = bar.tooltipPosition();

                            //if the bar is high enough -> write the percentageValue inside the bar
                            if (dataPercentage > 6) {
                                //if the bar is low enough -> write the amountValue above the bar
                                if (position.y > 15) {
                                    ctx.fillStyle = 'black';
                                    ctx.fillText(data, position.x, position.y - 10);


                                    if (vm.participants !== 0) {
                                        ctx.fillStyle = 'white';
                                        ctx.fillText(dataPercentage.toString() + "%", position.x, position.y + 10);
                                    }
                                }
                                //if the bar is too high -> write the amountValue inside the bar
                                else {
                                    ctx.fillStyle = 'white';
                                    if (vm.participants !== 0) {
                                        ctx.fillText(data + " / " + dataPercentage.toString() + "%", position.x, position.y + 10);
                                    } else {
                                        ctx.fillText(data, position.x, position.y + 10);
                                    }
                                }
                            }
                            //if the bar is to low -> write the percentageValue above the bar
                            else {
                                ctx.fillStyle = 'black';
                                if (vm.participants !== 0) {
                                    ctx.fillText(data + " / " + dataPercentage.toString() + "%", position.x, position.y - 10);
                                } else {
                                    ctx.fillText(data, position.x, position.y - 10);
                                }
                            }
                        });
                    });
                }
            }
        };
    }
})();
