export const programmingExerciseProblemStatementC = '### Tests\n' +
    '\n' +
    '#### General Tasks\n' +
    '1. [task][0 as Input](TestInput_0)\n' +
    '2. [task][1 as Input](TestInput_1)\n' +
    '3. [task][5 as Input](TestInput_5)\n' +
    '4. [task][7 as Input](TestInput_7)\n' +
    '5. [task][10 as Input](TestInput_10)\n' +
    '6. [task][Random Inputs](TestInputRandom_0, TestInputRandom_1, TestInputRandom_2, TestInputRandom_3, TestInputRandom_4)\n' +
    '\n' +
    '#### Address Sanitizer\n' +
    '1. [task][Address Sanitizer 1 as Input](TestInputASan_1)\n' +
    '2. [task][Address Sanitizer 5 as Input](TestInputASan_5)\n' +
    '\n' +
    '#### Leak Sanitizer\n' +
    '1. [task][Leak Sanitizer 1 as Input](TestInputLSan_1)\n' +
    '2. [task][Leak Sanitizer 5 as Input](TestInputLSan_5)\n' +
    '\n' +
    '#### Behaviour Sanitizer\n' +
    '1. [task][Undefined Behaviour Sanitizer 1 as Input](TestInputUBSan_1)\n' +
    '2. [task][Undefined Behaviour Sanitizer 5 as Input](TestInputUBSan_5)';

// TODO: Adjust to C
export const buildErrorContentC = {
    newFiles: [],
    content: [{
        fileName: 'src/de/test/BubbleSort.java',
        fileContent: 'a'
    }]
};

export const testErrorContentC = {
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

export const twoSuccessfulErrorContentC= {
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

export const allSuccessfulContentC = {
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
