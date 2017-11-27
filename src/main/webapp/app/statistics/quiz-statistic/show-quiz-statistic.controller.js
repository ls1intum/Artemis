(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('ShowQuizStatisticController', ShowQuizStatisticController);

    ShowQuizStatisticController.$inject = ['$translate','$scope', '$state', 'Principal', 'JhiWebsocketService', 'QuizExercise', 'QuizExerciseForStudent'];

    function ShowQuizStatisticController ($translate, $scope, $state, Principal, JhiWebsocketService, QuizExercise, QuizExerciseForStudent) {

        var vm = this;

        // Variables for the chart:
        var label;
        var ratedData;
        var unratedData;
        var backgroundColor;
        var ratedAverage;
        var unratedAverage;

        vm.switchRated = switchRated;
        vm.nextStatistic = nextStatistic;
        vm.releaseStatistics = releaseStatistics;
        var maxScore;

        vm.rated = true;
        vm.$onInit = init;


        function init(){
            if(Principal.hasAnyAuthority(['ROLE_ADMIN', 'ROLE_TA'])){
                QuizExercise.get({id: _.get($state,"params.quizId")}).$promise.then(loadQuizSuccess);
            }
            else{
                QuizExerciseForStudent.get({id: _.get($state,"params.quizId")}).$promise.then(loadQuizSuccess)
            }

            var websocketChannel = '/topic/statistic/'+ _.get($state,"params.quizId");

            JhiWebsocketService.subscribe(websocketChannel);

            JhiWebsocketService.receive(websocketChannel).then(null, null, function(notify) {
                if(Principal.hasAnyAuthority(['ROLE_ADMIN', 'ROLE_TA'])){
                    QuizExercise.get({id: _.get($state,"params.quizId")}).$promise.then(loadQuizSuccess);
                }
                else{
                    QuizExerciseForStudent.get({id: _.get($state,"params.quizId")}).$promise.then(loadQuizSuccess)
            }
            });

            $scope.$on('$destroy', function() {
                JhiWebsocketService.unsubscribe(websocketChannel);
            });

            $translate('showStatistic.quizStatistic.xAxes').then(function (xLabel){
                window.myChart.options.scales.xAxes[0].scaleLabel.labelString = xLabel;
            });
            $translate('showStatistic.quizStatistic.yAxes').then(function (yLabel){
                window.myChart.options.scales.yAxes[0].scaleLabel.labelString = yLabel;
            });
        }

        // This functions loads the Quiz, which is necessary to build the Web-Template.
        // And it loads the new Data if the Websocket has been notified
        function loadQuizSuccess(quiz){
            // if the Student finds a way to the Website, while the Statistic is not released -> the Student will be send back to Courses
            if( (!Principal.hasAnyAuthority(['ROLE_ADMIN', 'ROLE_TA'])) && quiz.quizPointStatistic.released == false){
                $state.go('courses');
            }
            vm.quizExercise = quiz;
            maxScore = calculateMaxScore();
            loadData();
        }

        function calculateMaxScore(){

            var result = 0;

            vm.quizExercise.questions.forEach(function(question){
                result = result + question.score
            });
            return result;
        }

        // load the Data from the Json-entity to the chart: myChart
        function loadData() {

            label = [];
            backgroundColor = [];
            ratedData = [];
            unratedData = [];
            ratedAverage = 0;
            unratedAverage = 0;

            //set data based on the CorrectCounters in the QuestionStatistics
            for(var i = 0; i < vm.quizExercise.questions.length; i++){
                label.push(i + 1);
                backgroundColor.push("#5bc0de");
                ratedData.push(vm.quizExercise.questions[i].questionStatistic.ratedCorrectCounter);
                unratedData.push(vm.quizExercise.questions[i].questionStatistic.unRatedCorrectCounter);
                ratedAverage = ratedAverage + (vm.quizExercise.questions[i].questionStatistic.ratedCorrectCounter * vm.quizExercise.questions[i].score);
                unratedAverage = unratedAverage + (vm.quizExercise.questions[i].questionStatistic.unRatedCorrectCounter * vm.quizExercise.questions[i].score);
            }

            //add data for the last bar (Average)
            backgroundColor.push("#1e3368");
            ratedData.push(ratedAverage / maxScore);
            unratedData.push(unratedAverage / maxScore);

            barChartData.labels = label;

            // load data into the chart
            // if vm.rated == true  -> load the rated data, else: load the unrated data
            if (vm.rated) {
                vm.participants = vm.quizExercise.quizPointStatistic.participantsRated;
                barChartData.participants = vm.quizExercise.quizPointStatistic.participantsRated;
                barChartData.datasets.forEach(function (dataset) {
                    dataset.data = ratedData;
                    dataset.backgroundColor = backgroundColor;
                });
            }
            else {
                vm.participants = vm.quizExercise.quizPointStatistic.participantsUnrated;
                barChartData.participants = vm.quizExercise.quizPointStatistic.participantsRated;
                barChartData.datasets.forEach(function (dataset) {
                    dataset.data = unratedData;
                    dataset.backgroundColor = backgroundColor;
                });
            }
            $translate('showStatistic.quizStatistic.average').then(function (lastLabel){
                label.push(lastLabel);
                window.myChart.update();
            });
        }

        // switch between the rated and the unrated Results
        function switchRated(){
            if(vm.rated) {
                barChartData.datasets.forEach(function (dataset) {
                    dataset.data = unratedData;
                });
                vm.participants = vm.quizExercise.quizPointStatistic.participantsUnrated;
                barChartData.participants = vm.quizExercise.quizPointStatistic.participantsUnrated;
                vm.rated = false;
            }
            else{
                barChartData.datasets.forEach(function (dataset) {
                    dataset.data = ratedData;
                });
                vm.participants = vm.quizExercise.quizPointStatistic.participantsRated;
                barChartData.participants = vm.quizExercise.quizPointStatistic.participantsRated;
                vm.rated = true;
            }
            window.myChart.update();
        }

        // got to the Template with the next Statistic -> the first QuestionStatistic
        // if there is no QuestionStatistic -> go to QuizPointStatistic
        function nextStatistic() {
            if(vm.quizExercise.questions === null || vm.quizExercise.questions.length === 0){
                $state.go('quiz-point-statistic-chart',{quizId: vm.quizExercise.id});
            }
            else{
                $state.go('multiple-choice-question-statistic-chart', {quizId: vm.quizExercise.id, questionId: vm.quizExercise.questions[0].id});
            }
        }

        //if released == true: releases all Statistics of the Quiz and saves it via REST-PUT
        //else:                 revoke all Statistics
        function releaseStatistics(released){
            if (released === vm.quizExercise.quizPointStatistic.released ){
                return;
            }
            if (released && !vm.quizExercise.isPlannedToStart || moment().isBefore(vm.quizExercise.dueDate)){
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

    }
})();
