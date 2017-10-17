(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('StatisticController', StatisticController);

    StatisticController.$inject = ['$scope', '$state', 'Principal'];

    function StatisticController ( $state, $scope, Principal) {

        var vm = this;

        vm.switchSolution = switchSolution;
        vm.unratedSolutions = unratedSolutions;
        vm.plusB =plusB;  //temporär

        var unratedData = [166, 23, 100, 144];
        var ratedData = [156, 43, 80, 166];
        var ratedCorrects = 132;
        var unratedCorrects = 142;

        var rated = true;
        var showSolution = false;

        barChartData.datasets.forEach(function (dataset) {
            if(rated)
                dataset.data = ratedData;
            else
                dataset.data = unratedData;
        })
        window.myChart.update();


        function plusB() {
            barChartData.datasets.forEach(function (dataset) {
                if(rated){
                    ratedData[1] ++;
                    dataset.data[1] = ratedData[1];
                }
                else {
                    unratedData[1] ++;
                    dataset.data[1] = unratedData[1];
                }
            })
            window.myChart.update();
        }

        function unratedSolutions(){
                if(rated) {
                    barChartData.datasets.forEach(function (dataset) {
                        dataset.data = unratedData.slice(0);
                        if (showSolution) {
                            dataset.data.push(unratedCorrects);
                            dataset.backgroundColor = ["#5cb85c", "#d9534f", "#d9534f", "#5cb85c", "#5bc0de"];
                        }
                        else {
                            dataset.backgroundColor = ["#5bc0de", "#5bc0de", "#5bc0de", "#5bc0de", "#5bc0de"];
                        }
                    })
                    document.getElementById("ratedButton").innerHTML = "<span class=\"glyphicon glyphicon-refresh\"></span>&nbsp;Zeige bewertete Antworten";
                    document.getElementById("text").innerHTML = "Antwortenverteilung (unbewertet)";
                    rated = false;
                }
                else{
                    barChartData.datasets.forEach(function (dataset) {
                        dataset.data = ratedData.slice(0);
                        if (showSolution) {
                            dataset.data.push(ratedCorrects);
                            dataset.backgroundColor = ["#5cb85c", "#d9534f", "#d9534f", "#5cb85c", "#5bc0de"];
                        }
                        else {
                            dataset.backgroundColor = ["#428bca", "#428bca", "#428bca", "#428bca", "#428bca"];
                        }
                    })
                    document.getElementById("ratedButton").innerHTML = "<span class=\"glyphicon glyphicon-refresh\"></span>&nbsp;Zeige unbewertete Antworten";
                    document.getElementById("text").innerHTML = "Antwortenverteilung (bewertet)";
                    rated = true;
                }
            window.myChart.update();
        }

        function switchSolution(){
            if(showSolution){
                barChartData.labels = ["A", "B", "C", "D", ["Richte", " Antwortkombination"]];

                barChartData.datasets.forEach(function (dataset) {
                    if (rated) {
                        dataset.data = ratedData.slice(0);
                        dataset.backgroundColor = ["#428bca", "#428bca", "#428bca", "#428bca", "#428bca"];
                    }
                    else {
                        dataset.data = unratedData.slice(0);
                        dataset.backgroundColor = ["#5bc0de", "#5bc0de", "#5bc0de", "#5bc0de", "#5bc0de"];
                    }
                })
                showSolution = false;
                document.getElementById("solutionButton").innerHTML = "<span class=\"glyphicon glyphicon-ok-circle\"></span>&nbsp;Zeige Lösung";
            }
            else {
                barChartData.labels = [["A","(richtig"], ["B","(falsch)"], ["C", "(falsch)"], ["D", "(richtig)"], ["Richte", " Antwortkombination"]]

                barChartData.datasets.forEach(function (dataset) {
                    dataset.backgroundColor = ["#5cb85c", "#d9534f", "#d9534f", "#5cb85c", "#5bc0de"];
                    if (rated) {
                        dataset.data = ratedData.slice(0);
                        if(dataset.data.length == ratedData.length)
                            dataset.data.push(ratedCorrects);
                    }
                    else {
                        dataset.data = unratedData.slice(0);
                        if(dataset.data.length == unratedData.length)
                            dataset.data.push(unratedCorrects);
                    }
                })
                showSolution = true;
                document.getElementById("solutionButton").innerHTML = "<span class=\"glyphicon glyphicon-remove-circle\"></span>&nbsp;Verberge Lösung";
                }
            window.myChart.update();
        }


        // Define a plugin to provide data labels

        // Chart.plugins.register({
        //     afterDatasetsDraw: function(chart, easing) {
        //         // To only draw at the end of animation, check for easing === 1
        //         var ctx = chart.ctx;
        //
        //         chart.data.datasets.forEach(function (dataset, i) {
        //             var meta = chart.getDatasetMeta(i);
        //             if (!meta.hidden) {
        //                 meta.data.forEach(function(element, index) {
        //                     // Draw the text in black, with the specified font
        //                     ctx.fillStyle = 'black';
        //
        //                     var fontSize = 12;
        //                     var fontStyle = 'normal';
        //                     var fontFamily = 'Arial';
        //                     ctx.font = Chart.helpers.fontString(fontSize, fontStyle, fontFamily);
        //
        //                     // Just naively convert to string for now
        //                     var dataString = dataset.data[index].toString();
        //                     var dataPersentageString = (Math.round((dataset.data[index] /213) *1000)/10).toString() + "%";
        //
        //                     // Make sure alignment settings are correct
        //                     ctx.textAlign = 'center';
        //                     ctx.textBaseline = 'middle';
        //
        //                     var padding = 5;
        //                     var position = element.tooltipPosition();
        //                     ctx.fillText(dataString, position.x, position.y - (fontSize / 2) - padding);
        //
        //                     ctx.fillStyle = 'white';
        //                     ctx.fillText(dataPersentageString, position.x, position.y + (fontSize / 2) + padding);
        //                 });
        //             }
        //         });
        //     }
        // });

    //     $scope.data ={
    //             labels: ["A", "B", "C", "D", ["Richte", " Antwortkombination"]], // Array ermöglicht Zeilenumbruch
    //             datasets: [{
    //                 //label: 'Anzahl der Antwort',
    //                 display: false,
    //                 data: [156, 43, 80, 166],
    //                 backgroundColor: ["#428bca", "#428bca", "#428bca", "#428bca", "#428bca"],
    //                 borderWidth: 0
    //             }]
    //         };
    //     $scope.options = {
    //             layout: {
    //                 padding: {
    //                     left: 50,
    //                     right: 50,
    //                     top: 0,
    //                     bottom: 30
    //                 }
    //             },
    //             legend: {
    //                 display: false
    //             },
    //             title: {
    //                 display: false,
    //                 text: "Antwortenverteilung bei 213 Teilnehmern",
    //                 position: "top",
    //                 fontSize: "16",
    //                 padding: 20
    //             },
    //             tooltips: {
    //                 enabled: false
    //             },
    //             scales: {
    //                 yAxes: [{
    //                     scaleLabel: {
    //                         labelString: 'Häufigkeit der Antwort',
    //                         display: true,
    //                     },
    //                     ticks: {
    //                         beginAtZero: true
    //                     }
    //                 }],
    //                 xAxes: [{
    //                     scaleLabel: {
    //                         labelString: 'Antwortmöglichkeiten',
    //                         display: true,
    //                     },
    //                 }]
    //             },
    //         }
    //
    //
    }
})();
