export const programmingExerciseProblemStatementPython =
    '# Sorting with the Strategy Pattern\n' +
    '\n' +
    'In this exercise, we want to implement sorting algorithms and choose them based on runtime specific variables.\n' +
    '\n' +
    '### Part 1: Sorting\n' +
    '\n' +
    'First, we need to implement two sorting algorithms, in this case `MergeSort` and `BubbleSort`.\n' +
    '\n' +
    '**You have the following tasks:**\n' +
    '\n' +
    '1. [task][Implement Bubble Sort](test_bubble_sort)\n' +
    'Implement the method `perform_sort(List<int>)` in the class `BubbleSort`. Make sure to follow the Bubble Sort algorithm exactly.\n' +
    '\n' +
    '2. [task][Implement Merge Sort](test_merge_sort)\n' +
    'Implement the method `perform_sort(List<int>)` in the class `MergeSort`. Make sure to follow the Merge Sort algorithm exactly.\n' +
    '\n' +
    '### Part 2: Strategy Pattern\n' +
    '\n' +
    'We want the application to apply different algorithms for sorting a `List` of `Int` objects.\n' +
    'Use the strategy pattern to select the right sorting algorithm at runtime.\n' +
    '\n' +
    '**You have the following tasks:**\n' +
    '\n' +
    '1. [task][SortStrategy Interface](test_sort_strategy_class,test_sort_strategy_methods)\n' +
    'Create a `SortStrategy` abstract class with an abstract method and adjust the sorting algorithms so that they inherit from this class.\n' +
    '\n' +
    '2. [task][Context Class](test_context_attributes,test_context_methods)\n' +
    'Create and implement a `Context` class following the below class diagram\n' +
    '\n' +
    '3. [task][Context Policy](test_policy_constructor,test_policy_attributes,test_policy_methods)\n' +
    'Create and implement a `Policy` class following the below class diagram with a simple configuration mechanism:\n' +
    '\n' +
    '1. [task][Select MergeSort](test_merge_sort_struct,test_merge_sort_for_big_list)\n' +
    'Select `MergeSort` when the List has more than 10 dates.\n' +
    '\n' +
    '2. [task][Select BubbleSort](test_bubble_sort_struct,test_bubble_sort_for_small_list)\n' +
    'Select `BubbleSort` when the List has less or equal 10 dates.\n' +
    '\n' +
    '4. Complete the `Client` class which demonstrates switching between two strategies at runtime.\n' +
    '\n' +
    '@startuml\n' +
    '\n' +
    'class Client {\n' +
    '}\n' +
    '\n' +
    'class Policy {\n' +
    '<color:testsColor(test_policy_methods)>+configure()</color>\n' +
    '}\n' +
    '\n' +
    'class Context {\n' +
    '<color:testsColor(test_context_attributes)>numbers: List<int></color>\n' +
    '<color:testsColor(test_context_methods)>+sort()</color>\n' +
    '}\n' +
    '\n' +
    'abstract class SortStrategy {\n' +
    '<color:testsColor(test_sort_strategy_methods)>+perform_sort(List<int>)</color>\n' +
    '}\n' +
    '\n' +
    'class BubbleSort {\n' +
    '<color:testsColor(test_bubble_sort_struct)>+performSort(List<int>)</color>\n' +
    '}\n' +
    '\n' +
    'class MergeSort {\n' +
    '<color:testsColor(test_merge_sort_struct)>+perform_sort(List<int>)</color>\n' +
    '}\n' +
    '\n' +
    'MergeSort -up-|> SortStrategy #testsColor(test_merge_sort_class)\n' +
    'BubbleSort -up-|> SortStrategy #testsColor(test_bubble_sort_class)\n' +
    'Policy -right-> Context #testsColor(test_policy_attributes): context\n' +
    'Context -right-> SortStrategy #testsColor(test_context_attributes): sortAlgorithm\n' +
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
    '1. Create a new class `QuickSort` that inherits from `SortStrategy` and implement the Quick Sort algorithm.\n' +
    '\n' +
    '2. Think about a useful decision in `Policy` when to use the new `QuickSort` algorithm.\n';

export const buildErrorContentPython = {
    newFiles: [],
    content: [
        {
            fileName: 'context.py',
            fileContent: 'a',
        },
    ],
};

export const someSuccessfulErrorContentPython = {
    newFiles: [],
    content: [
        {
            fileName: 'sort_strategy.py',
            fileContent:
                'from abc import ABC, abstractmethod\n' +
                '\n' +
                '\n' +
                'class SortStrategy(ABC):\n' +
                '\n' +
                '\t@abstractmethod\n' +
                '\tdef perform_sort(self, array):\n' +
                '\t\tpass',
        },
    ],
};

export const allSuccessfulContentPython = {
    newFiles: [],
    content: [
        {
            fileName: 'sorting_algorithms.py',
            fileContent:
                'from .sort_strategy import SortStrategy\n' +
                '\n' +
                '\n' +
                'class BubbleSort(SortStrategy):\n' +
                '\n' +
                '\tdef perform_sort(self, arr):\n' +
                '\t\tif arr is None:\n' +
                '\t\t\treturn\n' +
                '\n' +
                '\t\tfor i in range(len(arr))[::-1]:\n' +
                '\t\t\tfor j in range(i):\n' +
                '\t\t\t\tif arr[j] > arr[j + 1]:\n' +
                '\t\t\t\t\tarr[j], arr[j + 1] = arr[j + 1], arr[j]\n' +
                '\n' +
                '\n' +
                'class MergeSort(SortStrategy):\n' +
                '\n' +
                '\tdef perform_sort(self, arr):\n' +
                '\t\tself.__merge_sort(arr, 0, len(arr) - 1)\n' +
                '\n' +
                '\tdef __merge_sort(self, arr, low, high):\n' +
                '\t\tif high - low < 1:\n' +
                '\t\t\treturn\n' +
                '\n' +
                '\t\tmid = int((low + high) / 2)\n' +
                '\t\tself.__merge_sort(arr, low, mid)\n' +
                '\t\tself.__merge_sort(arr, mid + 1, high)\n' +
                '\t\tself.__merge(arr, low, mid, high)\n' +
                '\n' +
                '\tdef __merge(self, arr, low, mid, high):\n' +
                '\t\ttemp = [None] * (high - low + 1)\n' +
                '\n' +
                '\t\tleft_index = low\n' +
                '\t\tright_index = mid + 1\n' +
                '\t\twhole_index = 0\n' +
                '\n' +
                '\t\twhile left_index <= mid and right_index <= high:\n' +
                '\t\t\tif arr[left_index] <= arr[right_index]:\n' +
                '\t\t\t\ttemp[whole_index] = arr[left_index]\n' +
                '\t\t\t\tleft_index += 1\n' +
                '\t\t\telse:\n' +
                '\t\t\t\ttemp[whole_index] = arr[right_index]\n' +
                '\t\t\t\tright_index += 1\n' +
                '\t\t\twhole_index += 1\n' +
                '\n' +
                '\t\tif left_index <= mid and right_index > high:\n' +
                '\t\t\twhile left_index <= mid:\n' +
                '\t\t\t\ttemp[whole_index] = arr[left_index]\n' +
                '\t\t\t\twhole_index += 1\n' +
                '\t\t\t\tleft_index += 1\n' +
                '\t\telse:\n' +
                '\t\t\twhile right_index <= high:\n' +
                '\t\t\t\ttemp[whole_index] = arr[right_index]\n' +
                '\t\t\t\twhole_index += 1\n' +
                '\t\t\t\tright_index += 1\n' +
                '\n' +
                '\t\tfor whole_index in range(len(temp)):\n' +
                '\t\t\tarr[whole_index + low] = temp[whole_index]',
        },
        {
            fileName: 'policy.py',
            fileContent:
                'from .sorting_algorithms import *\n' +
                '\n' +
                '\n' +
                'class Policy:\n' +
                '\tcontext = None\n' +
                '\n' +
                '\tdef __init__(self, context):\n' +
                '\t\tself.context = context\n' +
                '\n' +
                '\tdef configure(self):\n' +
                '\t\tif len(self.context.numbers) > 10:\n' +
                "\t\t\tprint('More than 10 numbers, choosing merge sort!')\n" +
                '\t\t\tself.context.sorting_algorithm = MergeSort()\n' +
                '\t\telse:\n' +
                "\t\t\tprint('Less or equal than 10 numbers, choosing bubble sort!')\n" +
                '\t\t\tself.context.sorting_algorithm = BubbleSort()\n',
        },
        {
            fileName: 'context.py',
            fileContent:
                'package de.test;\n' +
                '\n' +
                'import java.text.*;\n' +
                'import java.util.*;\n' +
                '\n' +
                'public class Client {\n' +
                '\n' +
                '    /**\n' +
                '     * Main method.\n' +
                '     * Add code to demonstrate your implementation here.\n' +
                '     */\n' +
                '    public static void main(String[] args) throws ParseException {\n' +
                '\n' +
                '        // Init Context and Policy\n' +
                '\n' +
                '        Context sortingContext = new Context();\n' +
                '        Policy policy = new Policy(sortingContext);\n' +
                '\n' +
                '        // Run 10 times to simulate different sorting strategies\n' +
                '        for (int i = 0; i < 10; i++) {\n' +
                '            List<Date> dates = createRandomDatesList();\n' +
                '\n' +
                '            sortingContext.setDates(dates);\n' +
                '            policy.configure();\n' +
                '\n' +
                '            System.out.print("Unsorted Array of course dates = ");\n' +
                '            printDateList(dates);\n' +
                '\n' +
                '            sortingContext.sort();\n' +
                '\n' +
                '            System.out.print("Sorted Array of course dates = ");\n' +
                '            printDateList(dates);\n' +
                '        }\n' +
                '    }\n' +
                '\n' +
                '    /**\n' +
                '     * Generates an Array of random Date objects with random Array size between 5 and 15.\n' +
                '     */\n' +
                '    private static List<Date> createRandomDatesList() throws ParseException {\n' +
                '        int listLength = randomIntegerWithin(5, 15);\n' +
                '        List<Date> list = new ArrayList<>();\n' +
                '\n' +
                '        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");\n' +
                '        Date lowestDate = dateFormat.parse("08.11.2016");\n' +
                '        Date highestDate = dateFormat.parse("15.04.2017");\n' +
                '\n' +
                '        for (int i = 0; i < listLength; i++) {\n' +
                '            Date randomDate = randomDateWithin(lowestDate, highestDate);\n' +
                '            list.add(randomDate);\n' +
                '        }\n' +
                '        return list;\n' +
                '    }\n' +
                '\n' +
                '    /**\n' +
                '     * Creates a random Date within given Range\n' +
                '     */\n' +
                '    private static Date randomDateWithin(Date low, Date high) {\n' +
                '        long randomLong = randomLongWithin(low.getTime(), high.getTime());\n' +
                '        return new Date(randomLong);\n' +
                '    }\n' +
                '\n' +
                '    /**\n' +
                '     * Creates a random Long within given Range\n' +
                '     */\n' +
                '    private static long randomLongWithin(long low, long high) {\n' +
                '        return low + (long) (Math.random() * (high - low));\n' +
                '    }\n' +
                '\n' +
                '    /**\n' +
                '     * Creates a random Integer within given Range\n' +
                '     */\n' +
                '    private static int randomIntegerWithin(int low, int high) {\n' +
                '        return low + (int) (Math.random() * (high - low));\n' +
                '    }\n' +
                '\n' +
                '    /**\n' +
                '     * Prints out given Array of Date objects\n' +
                '     */\n' +
                '    private static void printDateList(List<Date> list) {\n' +
                '        System.out.println(list.toString());\n' +
                '    }\n' +
                '}',
        },
        {
            fileName: 'context.py',
            fileContent:
                'class Context:\n' +
                '\tsorting_algorithm = None\n' +
                '\tnumbers = None\n' +
                '\n' +
                '\tdef sort(self):\n' +
                '\t\tself.sorting_algorithm.perform_sort(self.numbers)\n',
        },
    ],
};
