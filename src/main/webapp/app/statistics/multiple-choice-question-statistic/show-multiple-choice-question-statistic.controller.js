(function () {
    'use strict';

    angular
        .module('artemisApp')
        .controller('ShowMultipleChoiceQuestionStatisticController', ShowMultipleChoiceQuestionStatisticController);

    ShowMultipleChoiceQuestionStatisticController.$inject = ['$translate', '$scope', '$state', 'Principal', 'JhiWebsocketService', 'QuizExercise', 'QuizExerciseForStudent', 'ArtemisMarkdown', 'QuizStatisticService'];

    function ShowMultipleChoiceQuestionStatisticController($translate, $scope, $state, Principal, JhiWebsocketService, QuizExercise, QuizExerciseForStudent, ArtemisMarkdown, QuizStatisticService) {

        var vm = this;

        // Variables for the chart:
        vm.labels = [];
        vm.data = [];
        vm.colors = [];

        var label;
        var solutionLabel;
        var ratedData;
        var unratedData;
        var backgroundColor;
        var backgroundSolutionColor;
        var ratedCorrectData;
        var unratedCorrectData;

        vm.switchSolution = switchSolution;
        vm.switchRated = switchRated;
        vm.nextStatistic = nextStatistic;
        vm.previousStatistic = previousStatistic;
        vm.releaseStatistics = releaseStatistics;
        vm.releaseButtonDisabled = releaseButtonDisabled;

        vm.showSolution = false;
        vm.rated = true;

        vm.$onInit = init;

        /**
         * loads quizExercise with the selected multipleChoiceQuestionStatistic
         * from server and sets up socket connections
         */
        function init() {
            // use different REST-call if the User is a Student
            if (Principal.hasAnyAuthority(['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'])) {
                QuizExercise.get({
                    id: _.get($state, "params.quizId")
                })
                    .$promise.then(loadQuiz, false);
            }
            else {
                QuizExerciseForStudent.get({
                    id: _.get($state, "params.quizId")
                })
                    .$promise.then(loadQuiz, false);
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
                     loadQuiz(quiz, true);

                });
            // refresh release information
            JhiWebsocketService.receive(websocketChannelForReleaseState)
                .then(null, null, function (payload) {
                    vm.quizExercise.quizPointStatistic.released = payload;
                    vm.questionStatistic.released = payload;
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
            $translate(
                'showStatistic.multipleChoiceQuestionStatistic.xAxes')
                .then(function (xLabel) {
                    vm.options.scales.xAxes[0].scaleLabel.labelString = xLabel;
                });
            $translate(
                'showStatistic.multipleChoiceQuestionStatistic.yAxes')
                .then(function (yLabel) {
                    vm.options.scales.yAxes[0].scaleLabel.labelString = yLabel;
                });
        }

        /**
         * This functions loads the Quiz, which is necessary to build the Web-Template
         *
         * @param {QuizExercise} quiz: the quizExercise, which the selected question is part of.
         * @param {boolean} refresh: true if method is called from Websocket
         */
        function loadQuiz(quiz, refresh) {
            // if the Student finds a way to the Website, while the Statistic is not released
            //      -> the Student will be send back to Courses
            if ((!Principal.hasAnyAuthority(['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA']))
                && quiz.quizPointStatistic.released === false) {
                $state.go('courses');
            }
            //search selected question in quizExercise based on questionId
            vm.quizExercise = quiz;
            vm.question = vm.quizExercise.questions.find( function (question) {
                // "==" because it compares a number with a string
                return _.get($state, "params.questionId") == question.id;
            });
            // if the Anyone finds a way to the Website,
            // with an wrong combination of QuizId and QuestionId
            //      -> go back to Courses
            if (vm.question === null) {
                $state.go('courses');
            }
            vm.questionStatistic = vm.question.questionStatistic;

            //load Layout only at the opening (not if the websocket refreshed the data)
            if (!refresh) {
                //render Markdown-text
                vm.questionTextRendered = ArtemisMarkdown.htmlForMarkdown(vm.question.text);
                vm.answerTextRendered = vm.question.answerOptions.map(function (answer) {
                    return ArtemisMarkdown.htmlForMarkdown(answer.text);
                });
                loadLayout();
            }
            loadData();
        }

        /**
         * build the Chart-Layout based on the the Json-entity (questionStatistic)
         */
        function loadLayout() {

            // reset old data
            label = [];
            backgroundColor = [];
            backgroundSolutionColor = new Array(vm.question.answerOptions.length + 1);
            solutionLabel = new Array(vm.question.answerOptions.length + 1);

            //set label and background-Color based on the AnswerOptions
            vm.question.answerOptions.forEach(function (answerOption, i) {
                label.push(String.fromCharCode(65 + i) + ".");
                backgroundColor.push(
                    {
                        backgroundColor: "#428bca",
                        borderColor: "#428bca",
                        pointBackgroundColor: "#428bca",
                        pointBorderColor: "#428bca"
                    });
            });
            addLastBarLayout();
            loadInvalidLayout();
            loadSolutionLayout();
        }

        /**
         * add Layout for the last bar
         */
        function addLastBarLayout() {
            //set backgroundColor for last bar
            backgroundColor.push(
                {
                    backgroundColor: "#5bc0de",
                    borderColor: "#5bc0de",
                    pointBackgroundColor: "#5bc0de",
                    pointBorderColor: "#5bc0de"
                });
            backgroundSolutionColor[vm.question.answerOptions.length] =
                {
                    backgroundColor: "#5bc0de",
                    borderColor: "#5bc0de",
                    pointBackgroundColor: "#5bc0de",
                    pointBorderColor: "#5bc0de"
                };

            //add Text for last label based on the language
            $translate('showStatistic.quizStatistic.yAxes').then(function (lastLabel) {
                solutionLabel[vm.question.answerOptions.length] = (lastLabel.split(" "));
                label[vm.question.answerOptions.length] = (lastLabel.split(" "));
                vm.labels = label;
            });
        }

        /**
         * change label and Color if a dropLocation is invalid
         */
        function loadInvalidLayout() {

            //set Background for invalid answers = grey
            $translate('showStatistic.invalid').then(function (invalidLabel) {
                vm.question.answerOptions.forEach(function (answerOption, i) {
                    if (answerOption.invalid) {
                        backgroundColor[i] = (
                            {
                                backgroundColor: "#838383",
                                borderColor: "#838383",
                                pointBackgroundColor: "#838383",
                                pointBorderColor: "#838383"
                            });
                        backgroundSolutionColor[i] = (
                            {
                                backgroundColor: "#838383",
                                borderColor: "#838383",
                                pointBackgroundColor: "#838383",
                                pointBorderColor: "#838383"
                            });

                        solutionLabel[i] = ([String.fromCharCode(65 + i)
                        + ".", " " + invalidLabel]);
                    }
                });
            });
        }

        /**
         * load Layout for showSolution
         */
        function loadSolutionLayout() {
            //add correct-text to the label based on the language
            $translate('showStatistic.multipleChoiceQuestionStatistic.correct')
                .then(function (correctLabel) {
                    vm.question.answerOptions.forEach(function (answerOption, i) {
                        if (answerOption.isCorrect) {
                            // check if the answer is valid and if true:
                            //      change solution-label and -color
                            if (!answerOption.invalid) {
                                backgroundSolutionColor[i] = (
                                    {
                                        backgroundColor: "#5cb85c",
                                        borderColor: "#5cb85c",
                                        pointBackgroundColor: "#5cb85c",
                                        pointBorderColor: "#5cb85c"
                                    });
                                solutionLabel[i] = ([String.fromCharCode(65 + i)
                                + ".", " (" + correctLabel + ")"]);
                            }
                        }
                    });
                });

            //add incorrect-text to the label based on the language
            $translate('showStatistic.multipleChoiceQuestionStatistic.incorrect')
                .then(function (incorrectLabel) {
                    vm.question.answerOptions.forEach(function (answerOption, i) {
                        if (!answerOption.isCorrect) {
                            // check if the answer is valid and if false:
                            //      change solution-label and -color
                            if (!answerOption.invalid) {
                                backgroundSolutionColor[i] = (
                                    {
                                        backgroundColor: "#d9534f",
                                        borderColor: "#d9534f",
                                        pointBackgroundColor: "#d9534f",
                                        pointBorderColor: "#d9534f"
                                    });
                                solutionLabel[i] = ([String.fromCharCode(65 + i)
                                + ".", " (" + incorrectLabel + ")"]);
                            }
                        }
                    });
                });
        }

        /**
         * load the Data from the Json-entity to the chart: myChart
         */
        function loadData() {

            // reset old data
            ratedData = [];
            unratedData = [];

            //set data based on the answerCounters for each AnswerOption
            vm.question.answerOptions.forEach(function (answerOption) {
                var answerOptionCounter = vm.questionStatistic.answerCounters
                    .find(function (answerCounter) {
                    return answerOption.id === answerCounter.answer.id;
                });
                ratedData.push(answerOptionCounter.ratedCounter);
                unratedData.push(answerOptionCounter.unRatedCounter);
            });
            //add data for the last bar (correct Solutions)
            ratedCorrectData = vm.questionStatistic.ratedCorrectCounter;
            unratedCorrectData = vm.questionStatistic.unRatedCorrectCounter;

            loadDataInDiagram();
        }

        /**
         * check if the rated or unrated
         * load the rated or unrated data into the diagram
         */
        function loadDataInDiagram() {

            // if show Solution is true use the
            // label, backgroundColor and Data, which show the solution
            if (vm.showSolution) {
                // show Solution
                vm.labels = solutionLabel;
                // if show Solution is true use the backgroundColor which shows the solution
                vm.colors = backgroundSolutionColor;
                if (vm.rated) {
                    vm.participants = vm.questionStatistic.participantsRated;
                    // if rated is true use the rated Data and add the rated CorrectCounter
                    vm.data = ratedData.slice(0);
                    vm.data.push(ratedCorrectData);
                }
                else {
                    vm.participants = vm.questionStatistic.participantsUnrated;
                    // if rated is false use the unrated Data and add the unrated CorrectCounter
                    vm.data = unratedData.slice(0);
                    vm.data.push(unratedCorrectData);
                }
            }
            else {
                // don't show Solution
                vm.labels = label;
                // if show Solution is false use the backgroundColor which doesn't show the solution
                vm.colors = backgroundColor;
                // if rated is true use the rated Data
                if (vm.rated) {
                    vm.participants = vm.questionStatistic.participantsRated;
                    vm.data = ratedData;
                }
                // if rated is false use the unrated Data
                else {
                    vm.participants = vm.questionStatistic.participantsUnrated;
                    vm.data = unratedData;
                }
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
         * switch between showing and hiding the solution in the chart
         *  1. change the BackgroundColor of the bars
         *  2. change the bar-Labels
         */
        function switchSolution() {
            vm.showSolution = !vm.showSolution;
            loadDataInDiagram();
        }

        /**
         * got to the Template with the previous Statistic
         * if first QuestionStatistic -> go to the Quiz-Statistic
         */
        function previousStatistic() {
            QuizStatisticService.previousStatistic(vm.quizExercise, vm.question);
        }

        /**
         * got to the Template with the next Statistic
         * if last QuestionStatistic -> go to the Quiz-Point-Statistic
         */
        function nextStatistic() {
            QuizStatisticService.nextStatistic(vm.quizExercise, vm.question);
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
                            var dataPercentage = (Math.round(
                                (dataset.data[index] / vm.participants) * 1000) / 10);

                            var position = bar.tooltipPosition();

                            //if the bar is high enough -> write the percentageValue inside the bar
                            if (dataPercentage > 6) {
                                //if the bar is low enough -> write the amountValue above the bar
                                if (position.y > 15) {
                                    ctx.fillStyle = 'black';
                                    ctx.fillText(data, position.x, position.y - 10);


                                    if (vm.participants !== 0) {
                                        ctx.fillStyle = 'white';
                                        ctx.fillText(dataPercentage.toString()
                                            + "%", position.x, position.y + 10);
                                    }
                                }
                                //if the bar is too high -> write the amountValue inside the bar
                                else {
                                    ctx.fillStyle = 'white';
                                    if (vm.participants !== 0) {
                                        ctx.fillText(data + " / " + dataPercentage.toString()
                                            + "%", position.x, position.y + 10);
                                    } else {
                                        ctx.fillText(data, position.x, position.y + 10);
                                    }
                                }
                            }
                            //if the bar is to low -> write the percentageValue above the bar
                            else {
                                ctx.fillStyle = 'black';
                                if (vm.participants !== 0) {
                                    ctx.fillText(data + " / " + dataPercentage.toString()
                                        + "%", position.x, position.y - 10);
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
