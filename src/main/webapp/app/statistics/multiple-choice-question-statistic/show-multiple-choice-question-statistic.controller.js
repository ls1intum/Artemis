(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('ShowMultipleChoiceQuestionStatisticController', ShowMultipleChoiceQuestionStatisticController);

    ShowMultipleChoiceQuestionStatisticController.$inject = ['$translate','$scope', '$state', 'Principal', 'JhiWebsocketService', 'QuizExercise', 'QuizExerciseForStudent' , 'MultipleChoiceQuestionStatistic', 'MultipleChoiceQuestionStatisticForStudent'];

    function ShowMultipleChoiceQuestionStatisticController ($translate, $scope, $state, Principal, JhiWebsocketService, QuizExercise, QuizExerciseForStudent, MultipleChoiceQuestionStatistic, MultipleChoiceQuestionStatisticForStudent) {

        var vm = this;

        // Variables for the chart:
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
        vm.quizIsOver = quizIsOver;


        vm.showSolution = false;
        vm.rated = true;
        vm.$onInit = init;

        function init(){
            if(Principal.hasAnyAuthority(['ROLE_ADMIN', 'ROLE_TA'])){
                QuizExercise.get({id: _.get($state,"params.quizId")}).$promise.then(loadQuiz);
            }
            else{
                QuizExerciseForStudent.get({id: _.get($state,"params.quizId")}).$promise.then(loadQuiz)
            }
            var websocketChannel = '/topic/statistic/'+ _.get($state,"params.quizId");

            JhiWebsocketService.subscribe(websocketChannel);

            JhiWebsocketService.receive(websocketChannel).then(null, null, function(notify) {
                if(Principal.hasAnyAuthority(['ROLE_ADMIN', 'ROLE_TA'])){
                    MultipleChoiceQuestionStatistic.get({id: vm.question.questionStatistic.id}).$promise.then(loadNewData);
                }
                else{
                    MultipleChoiceQuestionStatisticForStudent.get({id: vm.question.questionStatistic.id}).$promise.then(loadNewData);
                }
            });

            $scope.$on('$destroy', function() {
                JhiWebsocketService.unsubscribe(websocketChannel);
            });

            $translate('showStatistic.multipleChoiceQuestionStatistic.xAxes').then(function (xLabel){
                window.myChart.options.scales.xAxes[0].scaleLabel.labelString = xLabel;
            });
            $translate('showStatistic.multipleChoiceQuestionStatistic.yAxes').then(function (yLabel){
                window.myChart.options.scales.yAxes[0].scaleLabel.labelString = yLabel;
            });
        }

        // This functions loads the Quiz, which is necessary to build the Web-Template
        function loadQuiz(quiz) {
            // if the Student finds a way to the Website, while the Statistic is not released -> the Student will be send back to Courses
            if( (!Principal.hasAnyAuthority(['ROLE_ADMIN', 'ROLE_TA'])) && quiz.quizPointStatistic.released == false){
                $state.go('courses');
            }
            vm.quizExercise = quiz;
            vm.question = null;
            for(var i = 0; vm.question === null && i < vm.quizExercise.questions.length; i++){
                if (_.get($state,"params.questionId") == vm.quizExercise.questions[i].id){
                    vm.question = vm.quizExercise.questions[i];
                }
            }
            // if the Anyone finds a way to the Website, with an wrong combination of QuizId and QuestionId -> go back to Courses
            if(vm.question === null){
                $state.go('courses');
            }
            //MultipleChoiceQuestion.get({id: _.get($state,"params.questionId")}).$promise.then(loadQuestionSuccess);
            vm.questionStatistic = vm.question.questionStatistic;
            loadData();
        }

        // load the new Data if the Websocket has been notified
        function loadNewData(statistic){
            // if the Student finds a way to the Website, while the Statistic is not released -> the Student will be send back to Courses
            if( (!Principal.hasAnyAuthority(['ROLE_ADMIN', 'ROLE_TA'])) && quiz.quizPointStatistic.released == false){
                $state.go('courses');
            }
            vm.questionStatistic = statistic;
            loadData();
        }

        // load the Data from the Json-entity to the chart: myChart
        function loadData() {

            // reset old data
            label = new Array(vm.question.answerOptions.length);
            backgroundColor = [];
            backgroundSolutionColor = new Array(vm.question.answerOptions.length);
            ratedData = [];
            unratedData = [];
            solutionLabel = new Array(vm.question.answerOptions.length);

            //set data based on the answerCounters for each AnswerOption
            for(var i = 0; i < vm.question.answerOptions.length; i++){
                label[i] = (String.fromCharCode(65 + i));
                backgroundColor.push("#428bca");
                for(var j = 0; j < vm.questionStatistic.answerCounters.length; j++){
                    if (vm.question.answerOptions[i].id === (vm.questionStatistic.answerCounters[j].answer.id)){
                        ratedData.push(vm.questionStatistic.answerCounters[j].ratedCounter);
                        unratedData.push(vm.questionStatistic.answerCounters[j].unRatedCounter);
                    }
                }
            }
            //add data for the last bar (correct Solutions)
            ratedCorrectData = vm.questionStatistic.ratedCorrectCounter;
            unratedCorrectData = vm.questionStatistic.unRatedCorrectCounter;
            backgroundColor.push("#5bc0de");
            backgroundSolutionColor[vm.question.answerOptions.length] = ("#5bc0de");

            // load data into the chart
            // if vm.rated == true  -> load the rated data, else: load the unrated data
            if (vm.rated) {
                vm.participants = vm.questionStatistic.participantsRated;
                barChartData.participants = vm.questionStatistic.participantsRated;
                barChartData.datasets.forEach(function (dataset) {
                    dataset.data = ratedData.slice(0);
                    if(vm.showSolution){
                        dataset.backgroundColor = backgroundSolutionColor;
                            dataset.data.push(ratedCorrectData);

                    }else{
                        dataset.backgroundColor = backgroundColor;
                    }
                });
            }
            else {
                vm.participants = vm.questionStatistic.participantsUnrated;
                barChartData.participants = vm.questionStatistic.participantsRated;
                barChartData.datasets.forEach(function (dataset) {
                    dataset.data = unratedData.slice(0);
                    if(vm.showSolution){
                        dataset.backgroundColor = backgroundSolutionColor;
                        dataset.data.push(unratedCorrectData);
                    }else{
                        dataset.backgroundColor = backgroundColor;
                    }
                });
            }
            if(vm.showSolution){
                barChartData.labels = solutionLabel;

            }else{
                barChartData.labels = label;
            }

            $translate('showStatistic.quizStatistic.yAxes').then(function (lastLabel){
                solutionLabel.push(lastLabel);
                label.push(lastLabel);
                window.myChart.update();
            });

            $translate('showStatistic.multipleChoiceQuestionStatistic.correct').then(function (correctLabel){
                for(var i = 0; i < vm.question.answerOptions.length; i++) {
                    if (vm.question.answerOptions[i].isCorrect) {
                        backgroundSolutionColor[i] = ("#5cb85c");
                        solutionLabel[i] = ([String.fromCharCode(65 + i), " (" + correctLabel + ")"]);
                    }
                }
                window.myChart.update();
            });
            $translate('showStatistic.multipleChoiceQuestionStatistic.incorrect').then(function (incorrectLabel){
                for(var i = 0; i < vm.question.answerOptions.length; i++) {
                    if (!vm.question.answerOptions[i].isCorrect) {
                        backgroundSolutionColor[i] = ("#d9534f");
                        solutionLabel[i] = ([String.fromCharCode(65 + i), " (" + incorrectLabel + ")"]);
                    }
                }
                window.myChart.update();
            });

        }
        // switch between the rated and the unrated Results
        function switchRated(){
            if(vm.rated) {
                barChartData.datasets.forEach(function (dataset) {
                    dataset.data = unratedData.slice(0);
                    if(vm.showSolution){
                        dataset.backgroundColor = backgroundSolutionColor;
                        dataset.data.push(unratedCorrectData);
                    }else{
                        dataset.backgroundColor = backgroundColor;
                    }
                });
                vm.participants = vm.questionStatistic.participantsUnrated;
                barChartData.participants = vm.questionStatistic.participantsUnrated;
                vm.rated = false;
            }
            else{
                barChartData.datasets.forEach(function (dataset) {
                    dataset.data = ratedData.slice(0);
                    if(vm.showSolution){
                        dataset.backgroundColor = backgroundSolutionColor;
                        dataset.data.push(ratedCorrectData);
                    }else{
                        dataset.backgroundColor = backgroundColor;
                    }
                });
                vm.participants = vm.questionStatistic.participantsRated;
                barChartData.participants = vm.questionStatistic.participantsRated;
                vm.rated = true;
            }
            window.myChart.update();
        }

        // switch between showing and hiding the solution in the chart
        function switchSolution(){
            if(vm.showSolution){
                barChartData.datasets.forEach(function (dataset) {
                    if (vm.rated) {
                        dataset.data = ratedData.slice(0);
                    } else {
                        dataset.data = unratedData.slice(0);
                    }
                    dataset.backgroundColor = backgroundColor;
                });
                barChartData.labels = label;
                vm.showSolution = false;
            }
            else {
                barChartData.datasets.forEach(function (dataset) {
                    if (vm.rated) {
                        dataset.data = ratedData.slice(0);
                        dataset.data.push(ratedCorrectData);
                    }
                    else {
                        dataset.data = unratedData.slice(0);
                        dataset.data.push(unratedCorrectData);
                    }
                    dataset.backgroundColor = backgroundSolutionColor;
                });
                barChartData.labels = solutionLabel;
                vm.showSolution = true;
            }
            window.myChart.update();
        }

        // got to the Template with the previous Statistic
        //  if first QuestionStatistic -> go to the Quiz-Statistic
        function previousStatistic() {
            if(vm.quizExercise.questions[0].id === vm.question.id){
            $state.go('quiz-statistic-chart',{quizId: vm.quizExercise.id});
        }
        else{
            for (var i = 0; i < vm.quizExercise.questions.length; i++){
                if(vm.quizExercise.questions[i].id === vm.question.id){
                    $state.go('multiple-choice-question-statistic-chart', {quizId: vm.quizExercise.id, questionId: vm.quizExercise.questions[i-1].id});
                }
            }
        }

        }

        // got to the Template with the next Statistic
        //  if last QuestionStatistic -> go to the Quiz-Point-Statistic
        function nextStatistic() {
            if(vm.quizExercise.questions[vm.quizExercise.questions.length - 1].id === vm.question.id){
                $state.go('quiz-point-statistic-chart',{quizId: vm.quizExercise.id});
            }
            else{
                for (var i = 0; i < vm.quizExercise.questions.length; i++){
                    if(vm.quizExercise.questions[i].id === vm.question.id){
                        $state.go('multiple-choice-question-statistic-chart', {quizId: vm.quizExercise.id, questionId: vm.quizExercise.questions[i+1].id});
                    }
                }
            }
        }
        //if released == true: releases all Statistics of the Quiz and saves it via REST-PUT
        //else:                 revoke all Statistics
        function releaseStatistics(released){
            if (released === vm.quizExercise.quizPointStatistic.released ){
                return;
            }
            if (quizIsOver()){
                alert("Quiz noch nicht beendet!");
                return;
            }
            if (vm.quizExercise.id) {
                vm.quizExercise.quizPointStatistic.released = released;
                for (var i = 0; i < vm.quizExercise.questions.length; i++){
                    vm.quizExercise.questions[i].questionStatistic.released = released;
                }
                QuizExercise.update(vm.quizExercise);
            }
        }

        function quizIsOver(){
            return moment().isBefore(vm.quizExercise.dueDate);
        }

    }
})();
