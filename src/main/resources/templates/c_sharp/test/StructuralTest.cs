using System.Reflection;

namespace test;

public class StructuralTest
{
    private readonly Assembly assignment = Assembly.Load("assignment");

    [Test]
    public void TestISortStrategyType()
    {
        Type? type = assignment.GetType("assignment.ISortStrategy");

        Assert.That(type, Is.Not.Null);
        Assert.That(type.IsInterface, Is.True);
    }

    [Test]
    public void TestISortStrategyMethods()
    {
        Type? type = assignment.GetType("assignment.ISortStrategy");
        MethodInfo? method = type?.GetMethod("PerformSort", BindingFlags.Instance | BindingFlags.Public, [typeof(List<DateTime>)]);

        Assert.That(method, Is.Not.Null);
        Assert.That(method.ReturnType, Is.EqualTo(typeof(void)));
    }

    [Test]
    public void TestMergeSortInterface()
    {
        Type? type = assignment.GetType("assignment.MergeSort");
        Type? iface = type?.GetInterface("assignment.ISortStrategy");

        Assert.That(iface, Is.Not.Null);
    }

    [Test]
    public void TestBubbleSortInterface()
    {
        Type? type = assignment.GetType("assignment.BubbleSort");
        Type? iface = type?.GetInterface("assignment.ISortStrategy");

        Assert.That(iface, Is.Not.Null);
    }

    [Test]
    public void TestContextType()
    {
        Type? type = assignment.GetType("assignment.Context");

        Assert.That(type, Is.Not.Null);
        Assert.Multiple(() =>
        {
            Assert.That(type.IsClass, Is.True);
            Assert.That(type.IsAbstract, Is.False);
        });
    }

    [Test]
    public void TestContextPropertyDates()
    {
        Type? type = assignment.GetType("assignment.Context");
        PropertyInfo? datesProperty = type?.GetProperty("Dates", BindingFlags.Instance | BindingFlags.Public);

        Assert.That(datesProperty, Is.Not.Null);
        Assert.That(datesProperty.PropertyType, Is.EqualTo(typeof(List<DateTime>)));
    }

    [Test]
    public void TestContextPropertySortAlgorithm()
    {
        Type? type = assignment.GetType("assignment.Context");
        Type? sortStrategyType = assignment.GetType("assignment.ISortStrategy");
        PropertyInfo? sortAlgorithmProperty = type?.GetProperty("SortAlgorithm", BindingFlags.Instance | BindingFlags.Public);

        Assert.That(sortAlgorithmProperty, Is.Not.Null);
        Assert.That(sortAlgorithmProperty.PropertyType, Is.EqualTo(sortStrategyType));
    }

    [Test]
    public void TestContextMethods()
    {
        Type? type = assignment.GetType("assignment.Context");
        MethodInfo? method = type?.GetMethod("Sort", BindingFlags.Instance | BindingFlags.Public, []);

        Assert.That(method, Is.Not.Null);
        Assert.That(method.ReturnType, Is.EqualTo(typeof(void)));
    }

    [Test]
    public void TestPolicyType()
    {
        Type? type = assignment.GetType("assignment.Policy");

        Assert.That(type, Is.Not.Null);
        Assert.Multiple(() =>
        {
            Assert.That(type.IsClass, Is.True);
            Assert.That(type.IsAbstract, Is.False);
        });
    }

    [Test]
    public void TestPolicyPropertyContext()
    {
        Type? type = assignment.GetType("assignment.Policy");
        Type? contextType = assignment.GetType("assignment.Context");
        PropertyInfo? contextProperty = type?.GetProperty("Context", BindingFlags.Instance | BindingFlags.Public);

        Assert.That(contextProperty, Is.Not.Null);
        Assert.That(contextProperty.PropertyType, Is.EqualTo(contextType));
    }

    [Test]
    public void TestPolicyMethods()
    {
        Type? type = assignment.GetType("assignment.Policy");
        MethodInfo? method = type?.GetMethod("Configure", BindingFlags.Instance | BindingFlags.Public, []);

        Assert.That(method, Is.Not.Null);
        Assert.That(method.ReturnType, Is.EqualTo(typeof(void)));
    }

    [Test]
    public void TestPolicyConstructor()
    {
        Type? type = assignment.GetType("assignment.Policy");
        Type? contextType = assignment.GetType("assignment.Context");
        Assert.That(contextType, Is.Not.Null);

        ConstructorInfo? constructor = type?.GetConstructor(BindingFlags.Instance | BindingFlags.Public, [contextType]);
        Assert.That(constructor, Is.Not.Null);
    }
}
