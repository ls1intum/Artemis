(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('ShowMultipleChoiceQuestionStatisticController', ShowMultipleChoiceQuestionStatisticController);

    ShowMultipleChoiceQuestionStatisticController.$inject = [ '$rootScope','$scope', '$state', 'Principal', 'JhiWebsocketService', 'QuizExercise', 'MultipleChoiceQuestion', 'MultipleChoiceQuestionStatistic'];

    function ShowMultipleChoiceQuestionStatisticController ( rootScope, $scope, $state, Principal, JhiWebsocketService, QuizExercise, MultipleChoiceQuestion, MultipleChoiceQuestionStatistic) {

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
        vm.getCharacterByNumber = getCharacterByNumber;

        var showSolution = false;
        var rated = true;
        vm.$onInit = init;


        function init(){
            QuizExercise.get({id: _.get($state,"params.quizId")}).$promise.then(loadQuestion);

            var websocketChannel = '/topic/statistic/'+ "params.quizId";

            JhiWebsocketService.subscribe(websocketChannel);

            JhiWebsocketService.receive(websocketChannel).then(null, null, function(notify) {
                MultipleChoiceQuestionStatistic.get({id: _.get($state, vm.question.questionStatistic.id)}).$promise.then(loadNewData);
            });

            $scope.$on('$destroy', function() {
                JhiWebsocketService.unsubscribe(websocketChannel);
            })
        }

        function loadQuestion(quiz) {
            vm.quizExercise = quiz;
            MultipleChoiceQuestion.get({id: _.get($state,"params.questionId")}).$promise.then(loadQuestionSucces);
        }

        function loadQuestionSucces(question){
            vm.question = question;
            vm.questionStatistic = vm.question.questionStatistic;
            loadData();
        }

        function loadNewData(statistic){
            vm.questionStatistic = statistic;
            loadData();
        }


        function loadData() {

            label = [];
            backgroundColor = [];
            backgroundSolutionColor = [];
            ratedData = [];
            unratedData = [];
            solutionLabel = [];

            for(var i = 0; i < vm.question.answerOptions.length; i++){
                label.push(String.fromCharCode(65 + i));
                backgroundColor.push("#428bca");
                for(var j = 0; j < vm.questionStatistic.answerCounters.length; j++){
                    if (vm.question.answerOptions[i].id === (vm.questionStatistic.answerCounters[j].answer.id)){
                        ratedData.push(vm.questionStatistic.answerCounters[j].ratedCounter);
                        unratedData.push(vm.questionStatistic.answerCounters[j].unRatedCounter);
                        if(vm.questionStatistic.answerCounters[j].answer.isCorrect){
                            backgroundSolutionColor.push("#5cb85c");
                            solutionLabel.push([label[i]," (richtig)"]);
                        }else{
                            backgroundSolutionColor.push("#d9534f");
                            solutionLabel.push([label[i]," (falsch)"]);
                        }
                    }
                }
            }

            label.push(["Richte", " Lösungen"]);
            ratedCorrectData = vm.questionStatistic.ratedCorrectCounter;
            unratedCorrectData = vm.questionStatistic.unRatedCorrectCounter;
            backgroundColor.push("#5bc0de");
            backgroundSolutionColor.push("#5bc0de");
            solutionLabel.push(["Richte", " Lösungen"]);


            if (rated) {
                vm.participants = vm.questionStatistic.participantsRated;
                barChartData.participants = vm.questionStatistic.participantsRated;
                barChartData.datasets.forEach(function (dataset) {
                    dataset.data = ratedData.slice(0);
                    if(showSolution){
                        dataset.backgroundColor = backgroundSolutionColor;
                        //if(dataset.data.length == ratedData.length)
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
                    if(showSolution){
                        dataset.backgroundColor = backgroundSolutionColor;
                        //if(dataset.data.length == unratedData.length)
                        dataset.data.push(unratedCorrectData);
                    }else{
                        dataset.backgroundColor = backgroundColor;
                    }
                });
            }
            if(showSolution){
                barChartData.labels = solutionLabel;

            }else{
                barChartData.labels = label;
            }
            window.myChart.update();

        }

        function switchRated(){
            if(rated) {
                barChartData.datasets.forEach(function (dataset) {
                    dataset.data = unratedData.slice(0);
                    if(showSolution){
                        dataset.backgroundColor = backgroundSolutionColor;
                        //if(dataset.data.length == unratedData.length)
                        dataset.data.push(unratedCorrectData);
                    }else{
                        dataset.backgroundColor = backgroundColor;
                    }
                });
                document.getElementById("ratedButton").innerHTML = "<span class=\"glyphicon glyphicon-refresh\"></span>&nbsp;Zeige bewertete Ergebnisse";
                document.getElementById("text").innerHTML = "Antwortenverteilung (unbewertet)";
                vm.participants = vm.questionStatistic.participantsUnrated;
                barChartData.participants = vm.questionStatistic.participantsUnrated;
                rated = false;
            }
            else{
                barChartData.datasets.forEach(function (dataset) {
                    dataset.data = ratedData.slice(0);
                    if(showSolution){
                        dataset.backgroundColor = backgroundSolutionColor;
                        //if(dataset.data.length == ratedData.length)
                        dataset.data.push(ratedCorrectData);
                    }else{
                        dataset.backgroundColor = backgroundColor;
                    }
                });
                document.getElementById("ratedButton").innerHTML = "<span class=\"glyphicon glyphicon-refresh\"></span>&nbsp;Zeige unbewertete Ergebnisse";
                document.getElementById("text").innerHTML = "Antwortenverteilung (bewertet)";
                vm.participants = vm.questionStatistic.participantsRated;
                barChartData.participants = vm.questionStatistic.participantsRated;
                rated = true;
            }
            window.myChart.update();
        }

        function switchSolution(){
            if(showSolution){
                barChartData.datasets.forEach(function (dataset) {
                    if (rated) {
                        dataset.data = ratedData.slice(0);
                    } else {
                        dataset.data = unratedData.slice(0);
                    }
                    dataset.backgroundColor = backgroundColor;
                });
                barChartData.labels = label;
                showSolution = false;
                document.getElementById("solutionButton").innerHTML = "<span class=\"glyphicon glyphicon-ok-circle\"></span>&nbsp;Zeige Lösung";
            }
            else {
                barChartData.datasets.forEach(function (dataset) {
                    if (rated) {
                        dataset.data = ratedData.slice(0);
                        //if(dataset.data.length == ratedData.length)
                        dataset.data.push(ratedCorrectData);
                    }
                    else {
                        dataset.data = unratedData.slice(0);
                        //if(dataset.data.length == unratedData.length)
                        dataset.data.push(unratedCorrectData);
                    }
                    dataset.backgroundColor = backgroundSolutionColor;
                });
                barChartData.labels = solutionLabel;
                showSolution = true;
                document.getElementById("solutionButton").innerHTML = "<span class=\"glyphicon glyphicon-remove-circle\"></span>&nbsp;Verberge Lösung";
            }
            window.myChart.update();
        }

        function getCharacterByNumber(index){
            return String.fromCharCode(97 + index)
        }

        function previousStatistic() {

        }
        function nextStatistic() {

        }

    }
})();
