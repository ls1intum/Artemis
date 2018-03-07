(function () {
    'use strict';

    angular
        .module('artemisApp')
        .controller('ShowDragAndDropStatisticController', ShowDragAndDropStatisticController);

    ShowDragAndDropStatisticController.$inject = ['$translate', '$scope', '$state', 'Principal', 'JhiWebsocketService', 'QuizExercise', 'QuizExerciseForStudent', 'DragAndDropQuestionStatistic', 'DragAndDropQuestionStatisticForStudent', 'DragAndDropQuestionUtil', 'ArtemisMarkdown', 'QuizStatisticService'];

    function ShowDragAndDropStatisticController($translate, $scope, $state, Principal, JhiWebsocketService, QuizExercise, QuizExerciseForStudent, DragAndDropQuestionStatistic, DragAndDropQuestionStatisticForStudent, DragAndDropQuestionUtil, ArtemisMarkdown, QuizStatisticService) {

        var vm = this;

        // Variables for the chart:
        vm.labels = [];
        vm.data = [];
        vm.colors = [];

        var label;
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
        vm.getLetter = getLetter;
        vm.correctDragItemForDropLocation = correctDragItemForDropLocation;

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
                QuizExercise.get({id: _.get($state, "params.quizId")})
                    .$promise.then(loadQuiz);
            }
            else {
                QuizExerciseForStudent.get({id: _.get($state, "params.quizId")})
                    .$promise.then(loadQuiz);
            }
            //subscribe websocket for new statistical data
            var websocketChannelForData = '/topic/statistic/' + _.get($state, "params.quizId");
            JhiWebsocketService.subscribe(websocketChannelForData);

            //subscribe websocket which notifies the user if the release status was changed
            var websocketChannelForReleaseState = websocketChannelForData + '/release';
            JhiWebsocketService.subscribe(websocketChannelForReleaseState);

            // ask for new Data if the websocket for new statistical data was notified
            JhiWebsocketService.receive(websocketChannelForData).then(null, null, function (notify) {
                if (Principal.hasAnyAuthority(['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'])) {
                    DragAndDropQuestionStatistic.get({id: vm.questionStatistic.id})
                        .$promise.then(loadNewData);
                }
                else {
                    DragAndDropQuestionStatisticForStudent.get({id: vm.questionStatistic.id})
                        .$promise.then(loadNewData);
                }

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
            $translate('showStatistic.dragAndDropQuestionStatistic.xAxes').then(function (xLabel) {
                vm.options.scales.xAxes[0].scaleLabel.labelString = xLabel;
            });
            $translate('showStatistic.dragAndDropQuestionStatistic.yAxes').then(function (yLabel) {
                vm.options.scales.yAxes[0].scaleLabel.labelString = yLabel;
            });
        }

        /**
         * This functions loads the Quiz, which is necessary to build the Web-Template
         *
         * @param {QuizExercise} quiz: the quizExercise, which the selected question is part of.
         */
        function loadQuiz(quiz) {
            // if the Student finds a way to the Website, while the Statistic is not released
            //      -> the Student will be send back to Courses
            if ((!Principal.hasAnyAuthority(['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA']))
                && !quiz.quizPointStatistic.released) {
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

            vm.questionTextRendered = ArtemisMarkdown.htmlForMarkdown(vm.question.text);
            loadLayout();
            vm.questionStatistic = vm.question.questionStatistic;
            loadData();
        }

        /**
         * load the new dragAndDropQuestionStatistic from the server
         * if the Websocket has been notified
         *
         * @param {DragAndDropQuestionStatistic} statistic:
         *                          the new multipleChoiceQuestionStatistic
         *                          from the server with the new Data.
         */
        function loadNewData(statistic) {
            // if the Student finds a way to the Website, while the Statistic is not released
            //          -> the Student will be send back to Courses
            if ((!Principal.hasAnyAuthority(['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA']))
                && !quiz.quizPointStatistic.released) {
                $state.go('courses');
            }
            vm.questionStatistic = statistic;
            loadData();
        }

        /**
         * build the Chart-Layout based on the the Json-entity (questionStatistic)
         */
        function loadLayout() {

            orderDropLocationByPos();

            // reset old data
            label = new Array(vm.question.dropLocations.length + 1);
            backgroundColor = [];
            backgroundSolutionColor = [];

            //set label and backgroundcolor based on the dropLocations
            vm.question.dropLocations.forEach(function (dropLocation, i) {
                label[i] = (String.fromCharCode(65 + i) + ".");
                backgroundColor.push(
                    {
                        backgroundColor: "#428bca",
                        borderColor: "#428bca",
                        pointBackgroundColor: "#428bca",
                        pointBorderColor: "#428bca"
                    });
                backgroundSolutionColor.push(
                    {
                        backgroundColor: "#5cb85c",
                        borderColor: "#5cb85c",
                        pointBackgroundColor: "#5cb85c",
                        pointBorderColor: "#5cb85c"
                    });
            });

            addLastBarLayout();
            loadInvalidLayout();
        }

        /**
         * add Layout for the last bar
         */
        function addLastBarLayout() {
            //add Color for last bar
            backgroundColor.push(
                {
                    backgroundColor: "#5bc0de",
                    borderColor: "#5bc0de",
                    pointBackgroundColor: "#5bc0de",
                    pointBorderColor: "#5bc0de"
                });
            backgroundSolutionColor[vm.question.dropLocations.length] =
                {
                    backgroundColor: "#5bc0de",
                    borderColor: "#5bc0de",
                    pointBackgroundColor: "#5bc0de",
                    pointBorderColor: "#5bc0de"
                };

            //add Text for last label based on the language
            $translate('showStatistic.quizStatistic.yAxes').then(function (lastLabel) {
                label[vm.question.dropLocations.length] = (lastLabel.split(" "));
                vm.labels = label;
            });
        }

        /**
         * change label and Color if a dropLocation is invalid
         */
        function loadInvalidLayout() {

            //set Background for invalid answers = grey
            $translate('showStatistic.invalid').then(function (invalidLabel) {
                vm.question.dropLocations.forEach(function (dropLocation, i) {
                    if (dropLocation.invalid) {
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
                        // add "invalid" to bar-Label
                        label[i] = ([String.fromCharCode(65 + i) + ".", " " + invalidLabel]);
                    }
                });
            });
        }

        /**
         * load the Data from the Json-entity to the chart: myChart
         */
        function loadData() {

            // reset old data
            ratedData = new Array(vm.question.dropLocations.length);
            unratedData = new Array(vm.question.dropLocations.length);

            //set data based on the dropLocations for each dropLocation
            vm.question.dropLocations.forEach(function (dropLocation, i) {
                var dropLocationCounter = vm.questionStatistic.dropLocationCounters.find(function (dlCounter) {
                    return dropLocation.id === dlCounter.dropLocation.id;
                });
                ratedData[i] = dropLocationCounter.ratedCounter;
                unratedData[i] = dropLocationCounter.unRatedCounter;
            });
            //add data for the last bar (correct Solutions)
            ratedCorrectData = vm.questionStatistic.ratedCorrectCounter;
            unratedCorrectData = vm.questionStatistic.unRatedCorrectCounter;

            vm.labels = label;

            loadDataInDiagram();
        }

        /**
         * check if the rated or unrated
         * load the rated or unrated data into the diagram
         */
        function loadDataInDiagram() {

            // if show Solution is true use the label,
            // backgroundColor and Data, which show the solution
            if (vm.showSolution) {
                // show Solution
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
         */
        function switchRated() {
            vm.rated = !vm.rated;
            loadDataInDiagram();
        }

        /**
         * switch between showing and hiding the solution in the chart
         *  1. change the bar-Labels
         */
        function switchSolution() {
            vm.showSolution = !vm.showSolution;
            loadDataInDiagram();
        }

        /**
         * converts a number in a letter (0 -> A, 1 -> B, ...)
         *
         * @param index the given number
         */
        function getLetter(index) {
            return String.fromCharCode(65 + index);
        }

        /**
         * order DropLocations by Position
         */
        function orderDropLocationByPos() {
            var change = true;
            while (change) {
                change = false;
                for (var i = 0; i < vm.question.dropLocations.length - 1; i++) {
                    if ((vm.question.dropLocations[i].posX ) > vm.question.dropLocations[i + 1].posX) {
                        // switch DropLocations
                        var temp = vm.question.dropLocations[i];
                        vm.question.dropLocations[i] = vm.question.dropLocations[i + 1];
                        vm.question.dropLocations[i + 1] = temp;
                        change = true;
                    }
                }
            }
        }

        /**
         * Get the drag item that was mapped to the given drop location in the sample solution
         *
         * @param dropLocation {object} the drop location that the drag item should be mapped to
         * @return {object | null} the mapped drag item,
         *                          or null if no drag item has been mapped to this location
         */
        function correctDragItemForDropLocation(dropLocation) {
            var mapping = DragAndDropQuestionUtil.solve(vm.question, null).find(function (mapping) {
                return mapping.dropLocation.id === dropLocation.id;
            });
            if (mapping) {
                return mapping.dragItem;
            } else {
                return null;
            }
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
