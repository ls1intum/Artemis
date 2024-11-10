namespace assignment;

public static class Client
{

    private const int ITERATIONS = 10;

    private const int RANDOM_FLOOR = 5;

    private const int RANDOM_CEILING = 15;

    private static readonly Random random = new();

    /// <summary>
    /// Main method.
    /// Add code to demonstrate your implementation here.
    /// </summary>
    public static void Main()
    {
        // Init Context and Policy
        Context sortingContext = new();
        Policy policy = new(sortingContext);

        // Run multiple times to simulate different sorting strategies
        for (int i = 0; i < ITERATIONS; i++)
        {
            List<DateTime> dates = CreateRandomDatesList();

            sortingContext.Dates = dates;
            policy.Configure();

            Console.Write("Unsorted Array of course dates = ");
            PrintDateList(dates);

            sortingContext.Sort();

            Console.Write("Sorted Array of course dates = ");
            PrintDateList(dates);
        }
    }

    /// <summary>
    /// Generates a List of random Date objects with random List size between
    /// <c cref="RANDOM_FLOOR">RANDOM_FLOOR</c> and <c cref="RANDOM_CEILING">RANDOM_CEILING</c>.
    /// </summary>
    /// <returns>A List of random Date objects.</returns>
    private static List<DateTime> CreateRandomDatesList()
    {
        int listLength = random.Next(RANDOM_FLOOR, RANDOM_CEILING);
        List<DateTime> list = [];

        DateTime lowestDate = new(2024, 09, 15);
        DateTime highestDate = new(2025, 01, 15);

        for (int i = 0; i < listLength; i++)
        {
            DateTime randomDate = RandomDateWithin(lowestDate, highestDate);
            list.Add(randomDate);
        }
        return list;
    }

    /// <summary>
    /// Creates a random Date within the given range.
    /// </summary>
    /// <param name="low">the lower bound</param>
    /// <param name="high">the upper bound</param>
    /// <returns>A random <c>DateTime</c> within the given range.</returns>
    private static DateTime RandomDateWithin(DateTime low, DateTime high)
    {
        long randomTick = random.NextInt64(low.Ticks, high.Ticks);
        return new DateTime(randomTick);
    }

    /// <summary>
    /// Prints out the given Array of Date objects.
    /// </summary>
    /// <param name="list">list of the dates to print</param>
    private static void PrintDateList(List<DateTime> list)
    {
        var formattedDates = from date in list select date.ToString("O");
        var joinedDates = string.Join(", ", formattedDates);
        Console.WriteLine($"[{joinedDates}]");
    }
}
