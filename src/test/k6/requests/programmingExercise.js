import { nextAlphanumeric } from '../util/utils.js';
import { PROGRAMMING_EXERCISES_SETUP } from './endpoints.js';
import { sleep, fail } from 'k6';
import { PARTICIPATIONS, PROGRAMMING_EXERCISE } from './endpoints.js';

export function createExercise(artemis, courseId) {
    let res;
    let problemStatement = '# Sorting with the Strategy Pattern\n' +
        '\n' +
        'In this exercise, we want to implement sorting algorithms and choose them based on runtime specific variables.\n' +
        '\n' +
        '### Part 1: Sorting\n' +
        '\n' +
        'First, we need to implement two sorting algorithms, in this case `MergeSort` and `BubbleSort`.\n' +
        '\n' +
        '**You have the following tasks:**\n' +
        '\n' +
        '1. [task][Implement Bubble Sort](testBubbleSort)\n' +
        'Implement the method `performSort(List<Date>)` in the class `BubbleSort`. Make sure to follow the Bubble Sort algorithm exactly.\n' +
        '\n' +
        '2. [task][Implement Merge Sort](testMergeSort)\n' +
        'Implement the method `performSort(List<Date>)` in the class `MergeSort`. Make sure to follow the Merge Sort algorithm exactly.\n' +
        '\n' +
        '### Part 2: Strategy Pattern\n' +
        '\n' +
        'We want the application to apply different algorithms for sorting a `List` of `Date` objects.\n' +
        'Use the strategy pattern to select the right sorting algorithm at runtime.\n' +
        '\n' +
        '**You have the following tasks:**\n' +
        '\n' +
        '1. [task][SortStrategy Interface](testClass[SortStrategy],testMethods[SortStrategy])\n' +
        'Create a `SortStrategy` interface and adjust the sorting algorithms so that they implement this interface.\n' +
        '\n' +
        '2. [task][Context Class](testAttributes[Context],testMethods[Context])\n' +
        'Create and implement a `Context` class following the below class diagram\n' +
        '\n' +
        '3. [task][Context Policy](testConstructors[Policy],testAttributes[Policy],testMethods[Policy])\n' +
        'Create and implement a `Policy` class following the below class diagram with a simple configuration mechanism:\n' +
        '\n' +
        '    1. [task][Select MergeSort](testClass[MergeSort],testUseMergeSortForBigList)\n' +
        '    Select `MergeSort` when the List has more than 10 dates.\n' +
        '\n' +
        '    2. [task][Select BubbleSort](testClass[BubbleSort],testUseBubbleSortForSmallList)\n' +
        '    Select `BubbleSort` when the List has less or equal 10 dates.\n' +
        '\n' +
        '4. Complete the `Client` class which demonstrates switching between two strategies at runtime.\n' +
        '\n' +
        '@startuml\n' +
        '\n' +
        'class Client {\n' +
        '}\n' +
        '\n' +
        'class Policy {\n' +
        '  <color:testsColor(testMethods[Policy])>+configure()</color>\n' +
        '}\n' +
        '\n' +
        'class Context {\n' +
        '  <color:testsColor(testAttributes[Context])>-dates: List<Date></color>\n' +
        '  <color:testsColor(testMethods[Context])>+sort()</color>\n' +
        '}\n' +
        '\n' +
        'interface SortStrategy {\n' +
        '  <color:testsColor(testMethods[SortStrategy])>+performSort(List<Date>)</color>\n' +
        '}\n' +
        '\n' +
        'class BubbleSort {\n' +
        '  <color:testsColor(testMethods[BubbleSort])>+performSort(List<Date>)</color>\n' +
        '}\n' +
        '\n' +
        'class MergeSort {\n' +
        '  <color:testsColor(testMethods[MergeSort])>+performSort(List<Date>)</color>\n' +
        '}\n' +
        '\n' +
        'MergeSort -up-|> SortStrategy #testsColor(testClass[MergeSort])\n' +
        'BubbleSort -up-|> SortStrategy #testsColor(testClass[BubbleSort])\n' +
        'Policy -right-> Context #testsColor(testAttributes[Policy]): context\n' +
        'Context -right-> SortStrategy #testsColor(testAttributes[Context]): sortAlgorithm\n' +
        'Client .down.> Policy\n' +
        'Client .down.> Context\n' +
        '\n' +
        '@enduml\n' +
        '\n' +
        '\n' +
        '### Part 3: Optional Challenges\n' +
        '\n' +
        '(These are not tested)\n' +
        '\n' +
        '1. Create a new class `QuickSort` that implements `SortStrategy` and implement the Quick Sort algorithm.\n' +
        '\n' +
        '2. Make the method `performSort(List<Dates>)` generic, so that other objects can also be sorted by the same method.\n' +
        '**Hint:** Have a look at Java Generics and the interface `Comparable`.\n' +
        '\n' +
        '3. Think about a useful decision in `Policy` when to use the new `QuickSort` algorithm.\n';

    // The actual exercise
    let exercise = {
        title: 'TEST ' + nextAlphanumeric(10),
        shortName: 'TEST'+ nextAlphanumeric(5).toUpperCase(),
        maxScore: 42,
        assessmentType: 'AUTOMATIC',
        type: 'programming',
        programmingLanguage: 'JAVA',
        publishBuildPlanUrl: true,
        allowOnlineEditor: true,
        packageName: 'de.test',
        problemStatement: problemStatement,
        presentationScoreEnabled: false,
        sequentialTestRuns: true,
        course: {
            id: courseId
        }
    };

    res = artemis.post(PROGRAMMING_EXERCISES_SETUP, exercise);
    if (res[0].status !== 201) {
        fail('ERROR: Could not create exercise (' + res[0].status + ')! response was + ' + res[0].body);
    }
    let exerciseId = JSON.parse(res[0].body).id;
    console.log('CREATED new programming exercise, ID=' + exerciseId);

    return exerciseId;
}

export function deleteExercise(artemis, exerciseId) {
    let res = artemis.delete(PROGRAMMING_EXERCISE(exerciseId), {
        deleteStudentReposBuildPlans: true,
        deleteBaseReposBuildPlans: true
    });
    if (res[0].status !== 200) {
        fail('Could not delete exercise (' + res[0].status + ')! Response was + ' + res[0].body);
    }
    console.log('DELETED programming exercise, ID=' + exerciseId);
}

export function startExercise(artemis, courseId, exerciseId) {
    let res = artemis.post(PARTICIPATIONS(courseId, exerciseId), null, null);
    // console.log('RESPONSE of starting exercise: ' + res[0].body);

    if (res[0].status === 400) {
        sleep(3000);
        return;
    }

    if (res[0].status !== 201) {
        fail('ERROR trying to start exercise for ' + __VU + ':\n #####ERROR (' + res[0].status + ')##### ' + res[0].body);
    } else {
        console.log('SUCCESSFULLY started exercise for ' + __VU);
    }

    return JSON.parse(res[0].body).id;
}
