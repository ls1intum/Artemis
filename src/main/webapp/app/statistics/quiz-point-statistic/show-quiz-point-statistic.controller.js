(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('ShowQuizPointStatisticController', ShowQuizPointStatisticController);

    ShowQuizPointStatisticController.$inject = ['$rootScope', '$scope', '$state', 'Principal', 'JhiWebsocketService', 'QuizPointStatistic', 'QuizExercise'];

    function ShowQuizPointStatisticController(rootScope, $scope, $state, Principal, JhiWebsocketService, QuizPointStatistic, QuizExercise) {


        var vm = this;

        // Variables for the chart:
        var label;
        var ratedData;
        var unratedData;
        var backgroundColor;

        vm.switchRated = switchRated;
        vm.nextStatistic = nextStatistic;
        vm.previousStatistic = previousStatistic;

        var rated = true;
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
            })

        }

        function loadNewData(statistic){
            vm.quizPointStatistic = statistic;
            loadData();
        }

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


        function loadData() {

            label = [];
            backgroundColor = [];
            ratedData = [];
            unratedData = [];

            vm.quizPointStatistic.pointCounters.forEach(function (pointCounter) {
                label.push(pointCounter.points);
                ratedData.push(pointCounter.ratedCounter);
                unratedData.push(pointCounter.unRatedCounter);
                backgroundColor.push("#428bca");
            });
            order();

            barChartData.labels = label;

            if (rated) {
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

        function switchRated(){
            if(rated) {
                barChartData.datasets.forEach(function (dataset) {
                    dataset.data = unratedData;
                    dataset.backgroundColor = backgroundColor;
                });
                document.getElementById("ratedButton").innerHTML = "<span class=\"glyphicon glyphicon-refresh\"></span>&nbsp;Zeige bewertete Ergebnisse";
                document.getElementById("text").innerHTML = "Punkteverteilung (unbewertet)";
                vm.participants = vm.quizPointStatistic.participantsUnrated;
                barChartData.participants = vm.quizPointStatistic.participantsUnrated;
                rated = false;
            }
            else{
                barChartData.datasets.forEach(function (dataset) {
                    dataset.data = ratedData;
                    dataset.backgroundColor = backgroundColor;
                });
                document.getElementById("ratedButton").innerHTML = "<span class=\"glyphicon glyphicon-refresh\"></span>&nbsp;Zeige unbewertete Ergebnisse";
                document.getElementById("text").innerHTML = "Punkteverteilung (bewertet)";
                vm.participants = vm.quizPointStatistic.participantsRated;
                barChartData.participants = vm.quizPointStatistic.participantsRated;
                rated = true;
            }
            window.myChart.update();
        }

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

        function previousStatistic() {

            return "quiz-point-statistic-chart({quizId:vm.quizExercise.id})";
        }
        function nextStatistic() {

        }
    }
})();
