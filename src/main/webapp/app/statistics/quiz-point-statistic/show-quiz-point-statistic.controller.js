(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('ShowQuizPointStatisticController', ShowQuizPointStatisticController);

    ShowQuizPointStatisticController.$inject = ['$translate','$rootScope', '$scope', '$state', 'Principal', 'JhiWebsocketService', 'QuizPointStatistic', 'QuizExercise'];

    function ShowQuizPointStatisticController($translate, rootScope, $scope, $state, Principal, JhiWebsocketService, QuizPointStatistic, QuizExercise) {
        var vm = this;

        // Variables for the chart:
        var label;
        var ratedData;
        var unratedData;
        var backgroundColor;

        vm.switchRated = switchRated;
        vm.previousStatistic = previousStatistic;
        vm.releaseStatistics = releaseStatistics;

        vm.rated = true;
        vm.$onInit = init;


        function init(){
            QuizExercise.get({id: _.get($state,"params.quizId")}).$promise.then(loadQuizSucces);

            var websocketChannel = '/topic/statistic/'+ "params.quizId";

            JhiWebsocketService.subscribe(websocketChannel);

            JhiWebsocketService.receive(websocketChannel).then(null, null, function(notify) {
                QuizPointStatistic.get({id: _.get($state, vm.quizPointStatistic.id)}).$promise.then(loadNewData);
            });

            $scope.$on('$destroy', function() {
                JhiWebsocketService.unsubscribe(websocketChannel);
            });

            $translate('showStatistic.quizPointStatistic.xAxes').then(function (xLabel){
                window.myChart.options.scales.xAxes[0].scaleLabel.labelString = xLabel;
            });
            $translate('showStatistic.quizPointStatistic.yAxes').then(function (yLabel){
                window.myChart.options.scales.yAxes[0].scaleLabel.labelString = yLabel;
            });
        }

        // load the new Data if the Websocket has been notified
        function loadNewData(statistic){
            vm.quizPointStatistic = statistic;
            loadData();
        }

        // This functions loads the Quiz, which is necessary to build the Web-Template
        function loadQuizSucces(quiz){
            vm.quizExercise = quiz;
            vm.quizPointStatistic = vm.quizExercise.quizPointStatistic;
            vm.maxScore = calculateMaxScore();

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

            //set data based on the pointCounters
            vm.quizPointStatistic.pointCounters.forEach(function (pointCounter) {
                label.push(pointCounter.points);
                ratedData.push(pointCounter.ratedCounter);
                unratedData.push(pointCounter.unRatedCounter);
                backgroundColor.push("#428bca");
            });
            order();

            barChartData.labels = label;

            // load data into the chart
            // if vm.rated == true  -> load the rated data, else: load the unrated data
            if (vm.rated) {
                vm.participants = vm.quizPointStatistic.participantsRated;
                barChartData.participants = vm.quizPointStatistic.participantsRated;
                barChartData.datasets.forEach(function (dataset) {
                    dataset.data = ratedData;
                    dataset.backgroundColor = backgroundColor;
                });
            }
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

        // switch between the rated and the unrated Results
        function switchRated(){
            if(vm.rated) {
                barChartData.datasets.forEach(function (dataset) {
                    dataset.data = unratedData;
                });
                //document.getElementById("ratedButton").innerHTML = "<span class=\"glyphicon glyphicon-refresh\"></span>&nbsp;Zeige bewertete Ergebnisse";
                vm.participants = vm.quizPointStatistic.participantsUnrated;
                barChartData.participants = vm.quizPointStatistic.participantsUnrated;
                vm.rated = false;
            }
            else{
                barChartData.datasets.forEach(function (dataset) {
                    dataset.data = ratedData;
                });
                //document.getElementById("ratedButton").innerHTML = "<span class=\"glyphicon glyphicon-refresh\"></span>&nbsp;Zeige unbewertete Ergebnisse";
                vm.participants = vm.quizPointStatistic.participantsRated;
                barChartData.participants = vm.quizPointStatistic.participantsRated;
                vm.rated = true;
            }
            window.myChart.update();
        }

        //order the data, so that they are ascending
        function order(){
            var old = [];
            while (old.toString() !== label.toString()){
                old = label.slice();
                for(var i = 0; i < label.length-1; i ++){
                    if(label[i] > label[i+1]){
                        var temp = label[i];
                        label[i] = label[i+1];
                        label[i+1] = temp;
                        temp = ratedData[i];
                        ratedData[i] = ratedData[i+1];
                        ratedData[i+1] = temp;
                        temp = unratedData[i];
                        unratedData[i] = unratedData[i+1];
                        unratedData[i+1] = temp;
                    }
                }
            }
        }

        // got to the Template with the previous Statistic -> the last QuestionStatistic
        // if there is no QuestionStatistic -> go to QuizStatistic
        function previousStatistic() {
            if(vm.quizExercise.questions === null || vm.quizExercise.questions.length === 0){
                $state.go('quiz-statistic-chart',{quizId: vm.quizExercise.id});
            }
            else{
                $state.go('multiple-choice-question-statistic-chart', {quizId: vm.quizExercise.id, questionId: vm.quizExercise.questions[vm.quizExercise.questions.length -1].id});
            }
        }

        //if released == true: releases all Statistics of the Quiz and saves it via REST-PUT
        //else:                 revoke all Statistics
        function releaseStatistics(released){
            if (released === vm.quizExercise.quizPointStatistic.released){
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
