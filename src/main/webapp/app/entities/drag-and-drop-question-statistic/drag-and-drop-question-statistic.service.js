(function() {
    'use strict';
    angular
        .module('artemisApp')
        .factory('DragAndDropQuestionStatistic', DragAndDropQuestionStatistic);

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
})();
