using System.Reflection;

namespace test;

public class BehaviorTest
{
    private readonly Assembly assignment = Assembly.Load("assignment");
    private readonly List<DateTime> unorderedDates = [
        new(2018, 11, 08),
        new(2017, 04, 15),
        new(2016, 02, 15),
        new(2017, 09, 15),
    ];
    private List<DateTime> dates;


    [SetUp]
    public void Setup()
    {
        dates = new List<DateTime>(unorderedDates);
    }

    [Test, Timeout(1000)]
    public void TestBubbleSort()
    {
        BubbleSort bubbleSort = new();

        bubbleSort.PerformSort(dates);

        Assert.Multiple(() =>
        {
            Assert.That(dates, Is.Ordered);
            Assert.That(dates, Is.EquivalentTo(unorderedDates));
        });
    }

    [Test, Timeout(1000)]
    public void TestMergeSort()
    {
        MergeSort mergeSort = new();

        mergeSort.PerformSort(dates);

        Assert.Multiple(() =>
        {
            Assert.That(dates, Is.Ordered);
            Assert.That(dates, Is.EquivalentTo(unorderedDates));
        });
    }

    [Test, Timeout(1000)]
    public void TestUseMergeSortForBigList()
    {
        List<DateTime> bigList = Enumerable.Repeat(new DateTime(), 11).ToList();

        object? sortStrategy = ConfigurePolicyAndContext(bigList);

        Assert.That(sortStrategy, Is.InstanceOf<MergeSort>());
    }

    [Test, Timeout(1000)]
    public void TestUseBubbleSortForSmallList()
    {
        List<DateTime> smallList = Enumerable.Repeat(new DateTime(), 3).ToList();

        object? sortStrategy = ConfigurePolicyAndContext(smallList);

        Assert.That(sortStrategy, Is.InstanceOf<BubbleSort>());
    }

    /// <summary>
    /// Configures the sorting policy and context using reflection.
    /// </summary>
    /// <param name="dates">The list of dates to be sorted</param>
    /// <returns>The configured sorting algorithm instance</returns>
    private object? ConfigurePolicyAndContext(List<DateTime> dates)
    {
        Type? contextType = assignment.GetType("assignment.Context");
        Assert.That(contextType, Is.Not.Null);
        object? contextInstance = Activator.CreateInstance(contextType);
        Assert.That(contextInstance, Is.Not.Null);

        PropertyInfo? datesProperty = contextType.GetProperty("Dates");
        Assert.That(datesProperty, Is.Not.Null);
        datesProperty.SetValue(contextInstance, dates);

        Type? policyType = assignment.GetType("assignment.Policy");
        Assert.That(policyType, Is.Not.Null);
        object? policyInstance = Activator.CreateInstance(policyType, contextInstance);
        Assert.That(policyInstance, Is.Not.Null);

        MethodInfo? configureMethod = policyType.GetMethod("Configure");
        Assert.That(configureMethod, Is.Not.Null);
        configureMethod.Invoke(policyInstance, []);

        PropertyInfo? sortAlgorithmProperty = contextType.GetProperty("SortAlgorithm");
        Assert.That(sortAlgorithmProperty, Is.Not.Null);
        object? sortAlgorithm = sortAlgorithmProperty.GetValue(contextInstance);

        return sortAlgorithm;
    }
}