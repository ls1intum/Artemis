export const programmingExerciseProblemStatementJava = '# Sorting with the Strategy Pattern\n' +
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

export const buildErrorContentJava = {
    newFiles: [],
    content: [{
        fileName: 'src/de/test/BubbleSort.java',
        fileContent: 'a'
    }]
};

export const testErrorContentJava = {
    newFiles: [],
    content: [{
        fileName: 'src/de/test/BubbleSort.java',
        fileContent: 'package de.test;\n' +
            '\n' +
            'import java.util.*;\n' +
            '\n' +
            'public class BubbleSort {\n' +
            '\n' +
            '    public void performSort(List<Date> input) {}\n' +
            '}'
    }]
};

export const someSuccessfulErrorContentJava = {
    newFiles: ['src/de/test/SortStrategy.java'],
    content: [{
        fileName: 'src/de/test/SortStrategy.java',
        fileContent: 'package de.test;\n' +
            '\n' +
            'import java.util.Date;\n' +
            'import java.util.List;\n' +
            '\n' +
            'public interface SortStrategy {\n' +
            '\n' +
            '\tpublic void performSort(List<Date> input);\n' +
            '}'
    }]
};

export const allSuccessfulContentJava = {
    newFiles: ['src/de/test/Context.java', 'src/de/test/Policy.java'],
    content: [
        {
            fileName: 'src/de/test/Context.java',
            fileContent: 'package de.test;\n' +
                '\n' +
                'import java.util.Date;\n' +
                'import java.util.List;\n' +
                '\n' +
                'public class Context {\n' +
                '\n' +
                '\tprivate SortStrategy sortAlgorithm;\n' +
                '\n' +
                '\tprivate List<Date> dates;\n' +
                '\n' +
                '\tpublic List<Date> getDates() {\n' +
                '\t\treturn dates;\n' +
                '\t}\n' +
                '\n' +
                '\tpublic void setDates(List<Date> dates) {\n' +
                '\t\tthis.dates = dates;\n' +
                '\t}\n' +
                '\n' +
                '\tpublic void setSortAlgorithm(SortStrategy sa) {\n' +
                '\t\tsortAlgorithm = sa;\n' +
                '\t}\n' +
                '\n' +
                '\tpublic SortStrategy getSortAlgorithm() {\n' +
                '\t\treturn sortAlgorithm;\n' +
                '\t}\n' +
                '\n' +
                '\tpublic void sort() {\n' +
                '\t\tsortAlgorithm.performSort(this.dates);\n' +
                '\t}\n' +
                '}',
        },
        {
            fileName: 'src/de/test/BubbleSort.java',
            fileContent: 'package de.test;\n' +
                '\n' +
                'import java.util.*;\n' +
                '\n' +
                'public class BubbleSort implements SortStrategy {\n' +
                '\n' +
                '\tpublic void performSort(List<Date> input) {\n' +
                '\n' +
                '\t\tfor (int i = input.size() - 1; i >= 0; i--) {\n' +
                '\t\t\tfor (int j = 0; j < i; j++) {\n' +
                '\t\t\t\tif (input.get(j).compareTo(input.get(j + 1)) > 0) {\n' +
                '\t\t\t\t\tDate temp = input.get(j);\n' +
                '\t\t\t\t\tinput.set(j, input.get(j + 1));\n' +
                '\t\t\t\t\tinput.set(j + 1, temp);\n' +
                '\t\t\t\t}\n' +
                '\t\t\t}\n' +
                '\t\t}\n' +
                '\n' +
                '\t}\n' +
                '}'
        },
        {
            fileName: "src/de/test/Client.java",
            fileContent: 'package de.test;\n' +
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
                '}'
        },
        {
            fileName: "src/de/test/MergeSort.java",
            fileContent: 'package de.test;\n' +
                '\n' +
                'import java.util.*;\n' +
                '\n' +
                'public class MergeSort implements SortStrategy {\n' +
                '\n' +
                '    // Wrapper method for the real algorithm.\n' +
                '    public void performSort(List<Date> input) {\n' +
                '        mergesort(input, 0, input.size() - 1);\n' +
                '    }\n' +
                '\n' +
                '    // Recursive merge sort method\n' +
                '    private void mergesort(List<Date> input, int low, int high) {\n' +
                '        if (high - low < 1) {\n' +
                '            return;\n' +
                '        }\n' +
                '        int mid = (low + high) / 2;\n' +
                '        mergesort(input, low, mid);\n' +
                '        mergesort(input, mid + 1, high);\n' +
                '        merge(input, low, mid, high);\n' +
                '    }\n' +
                '\n' +
                '    // Merge method\n' +
                '    private void merge(List<Date> input, int low, int middle, int high) {\n' +
                '\n' +
                '        Date[] temp = new Date[high - low + 1];\n' +
                '        int leftIndex = low;\n' +
                '        int rightIndex = middle + 1;\n' +
                '        int wholeIndex = 0;\n' +
                '        while (leftIndex <= middle && rightIndex <= high) {\n' +
                '            if (input.get(leftIndex).compareTo(input.get(rightIndex)) <= 0) {\n' +
                '                temp[wholeIndex] = input.get(leftIndex++);\n' +
                '            }\n' +
                '            else {\n' +
                '                temp[wholeIndex] = input.get(rightIndex++);\n' +
                '            }\n' +
                '            wholeIndex++;\n' +
                '        }\n' +
                '        if (leftIndex <= middle && rightIndex > high) {\n' +
                '            while (leftIndex <= middle) {\n' +
                '                temp[wholeIndex++] = input.get(leftIndex++);\n' +
                '            }\n' +
                '        }\n' +
                '        else {\n' +
                '            while (rightIndex <= high) {\n' +
                '                temp[wholeIndex++] = input.get(rightIndex++);\n' +
                '            }\n' +
                '        }\n' +
                '        for (wholeIndex = 0; wholeIndex < temp.length; wholeIndex++) {\n' +
                '            input.set(wholeIndex + low, temp[wholeIndex]);\n' +
                '        }\n' +
                '    }\n' +
                '}'
        },
        {
            fileName: 'src/de/test/Policy.java',
            fileContent: 'package de.test;\n' +
                '\n' +
                'public class Policy {\n' +
                '\t\n' +
                '\tprivate Context context;\n' +
                '\n' +
                '\tpublic Policy(Context context) {\n' +
                '\t\tthis.context = context;\n' +
                '\t}\n' +
                '\n' +
                '\tpublic void configure() {\n' +
                '\t\tif(this.context.getDates().size() > 10) {\n' +
                '\t\t\tSystem.out.println("More than 10 dates, choosing merge sort!");\n' +
                '\t\t\tthis.context.setSortAlgorithm(new MergeSort());\n' +
                '\t\t} else {\n' +
                '\t\t\tSystem.out.println("Less or equal than 10 dates. choosing quick sort!");\n' +
                '\t\t\tthis.context.setSortAlgorithm(new BubbleSort());\n' +
                '\t\t}\n' +
                '\t}\n' +
                '}'
        }
    ]
};
