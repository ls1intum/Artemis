
//Variable for the statistical Data
barChartData = {
    labels: [],
    datasets: [{
        display: false,
        data: [],
        backgroundColor: [],
        borderWidth: 0
    }]
};

if(document.getElementById("myChart") !== null) {
    //create Chart Variable
    var ctx = document.getElementById("myChart").getContext('2d');
    window.myChart = new Chart(ctx, {
        type: 'bar',
        data: barChartData,
        options: {
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
                            var dataPercentage = (Math.round((dataset.data[index] / barChartData.participants) * 1000) / 10);

                            var position = bar.tooltipPosition();

                            //if the bar is high enough -> write the percentageValue inside the bar
                            if (dataPercentage > 6) {
                                //if the bar is low enough -> write the amountValue above the bar
                                if (position.y > 15) {
                                    ctx.fillStyle = 'black';
                                    ctx.fillText(data, position.x, position.y - 10);


                                    if (barChartData.participants !== 0) {
                                        ctx.fillStyle = 'white';
                                        ctx.fillText(dataPercentage.toString() + "%", position.x, position.y + 10);
                                    }
                                }
                                //if the bar is too high -> write the amountValue inside the bar
                                else {
                                    ctx.fillStyle = 'white';
                                    if (barChartData.participants !== 0) {
                                        ctx.fillText(data + " / " + dataPercentage.toString() + "%", position.x, position.y + 10);
                                    } else {
                                        ctx.fillText(data, position.x, position.y + 10);
                                    }
                                }
                            }
                            //if the bar is to low -> write the percentageValue above the bar
                            else {
                                ctx.fillStyle = 'black';
                                if (barChartData.participants !== 0) {
                                    ctx.fillText(data + " / " + dataPercentage.toString() + "%", position.x, position.y - 10);
                                } else {
                                    ctx.fillText(data, position.x, position.y - 10);
                                }
                            }
                        });
                    });
                }
            }
        }
    });
}
