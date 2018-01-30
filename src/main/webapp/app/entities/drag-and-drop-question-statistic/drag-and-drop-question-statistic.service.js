(function() {
    'use strict';
    angular
        .module('artemisApp')
        .factory('DragAndDropQuestionStatistic', DragAndDropQuestionStatistic)
        .factory('DragAndDropQuestionStatisticForStudent', DragAndDropQuestionStatisticForStudent);

    DragAndDropQuestionStatistic.$inject = ['$resource'];

    function DragAndDropQuestionStatistic ($resource) {
        var resourceUrl =  'api/drag-and-drop-question-statistics/:id';

        return $resource(resourceUrl, {}, {
            'query': { method: 'GET', isArray: true},
            'get': {
                method: 'GET',
                transformResponse: function (data) {
                    if (data) {
                        data = angular.fromJson(data);
                    }
                    return data;
                }
            },
            'update': { method:'PUT' }
        });
    }

    DragAndDropQuestionStatisticForStudent.$inject = ['$resource'];

    function DragAndDropQuestionStatisticForStudent ($resource) {
        var resourceUrl =  'api/drag-and-drop-question-statistics/:id/for-student';

        return $resource(resourceUrl, {}, {
            'get': {
                method: 'GET',
                transformResponse: function (data) {
                    if (data) {
                        data = angular.fromJson(data);
                    }
                    return data;
                }
            }
        });
    }
})();
