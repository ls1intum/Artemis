(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('ShowQuizStatisticController', ShowQuizStatisticController);

    ShowQuizStatisticController.$inject = ['$translate', '$rootScope','$scope', '$state', 'Principal', 'JhiWebsocketService', 'QuizExercise'];

    function ShowQuizStatisticController ($translate, rootScope, $scope, $state, Principal, JhiWebsocketService, QuizExercise) {

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

        var maxScore;

        vm.rated = true;
        vm.$onInit = init;


        function init(){
            QuizExercise.get({id: _.get($state,"params.quizId")}).$promise.then(loadQuizSuccess);

            var websocketChannel = '/topic/statistic/'+ "params.quizId";

            JhiWebsocketService.subscribe(websocketChannel);

            JhiWebsocketService.receive(websocketChannel).then(null, null, function(notify) {
                QuizExercise.get({id: _.get($state,"params.quizId")}).$promise.then(loadQuizSuccess);
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

        function loadQuizSuccess(quiz){
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

        function loadData() {

            label = [];
            backgroundColor = [];
            ratedData = [];
            unratedData = [];
            ratedAverage = 0;
            unratedAverage = 0;

            for(var i = 0; i < vm.quizExercise.questions.length; i++){
                label.push(i + 1);
                backgroundColor.push("#5bc0de");
                ratedData.push(vm.quizExercise.questions[i].questionStatistic.ratedCorrectCounter);
                unratedData.push(vm.quizExercise.questions[i].questionStatistic.unRatedCorrectCounter);
                ratedAverage = ratedAverage + (vm.quizExercise.questions[i].questionStatistic.ratedCorrectCounter * vm.quizExercise.questions[i].score);
                unratedAverage = unratedAverage + (vm.quizExercise.questions[i].questionStatistic.unRatedCorrectCounter * vm.quizExercise.questions[i].score);
            }

            label.push("Durchschnitt");
            backgroundColor.push("#1e3368");
            ratedData.push(ratedAverage / maxScore);
            unratedData.push(unratedAverage / maxScore);

            barChartData.labels = label;

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
            window.myChart.update();
        }

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

        function nextStatistic() {
            if(vm.quizExercise.questions === null || vm.quizExercise.questions.length === 0){
                $state.go('quiz-point-statistic-chart',{quizId: vm.quizExercise.id});
            }
            else{
                $state.go('multiple-choice-question-statistic-chart', {quizId: vm.quizExercise.id, questionId: vm.quizExercise.questions[0].id});
            }
        }

    }
})();
