'use strict';

describe('Controller Tests', function() {

    describe('QuizExercise Management Detail Controller', function() {
        var $scope, $rootScope;
        var MockEntity, MockPreviousState, MockQuizExercise, MockQuizPointStatistic, MockQuestion;
        var createController;

        beforeEach(inject(function($injector) {
            $rootScope = $injector.get('$rootScope');
            $scope = $rootScope.$new();
            MockEntity = jasmine.createSpy('MockEntity');
            MockPreviousState = jasmine.createSpy('MockPreviousState');
            MockQuizExercise = jasmine.createSpy('MockQuizExercise');
            MockQuizPointStatistic = jasmine.createSpy('MockQuizPointStatistic');
            MockQuestion = jasmine.createSpy('MockQuestion');
            

            var locals = {
                '$scope': $scope,
                '$rootScope': $rootScope,
                'entity': MockEntity,
                'previousState': MockPreviousState,
                'QuizExercise': MockQuizExercise,
                'QuizPointStatistic': MockQuizPointStatistic,
                'Question': MockQuestion
            };
            createController = function() {
                $injector.get('$controller')("QuizExerciseDetailController", locals);
            };
        }));


        describe('Root Scope Listening', function() {
            it('Unregisters root scope listener upon scope destruction', function() {
                var eventType = 'artemisApp:quizExerciseUpdate';

                createController();
                expect($rootScope.$$listenerCount[eventType]).toEqual(1);

                $scope.$destroy();
                expect($rootScope.$$listenerCount[eventType]).toBeUndefined();
            });
        });
    });

});
