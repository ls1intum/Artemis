namespace assignment;

public interface ISortStrategy
{
    /// <summary>
    /// Sorts a list of Dates.
    /// </summary>
    /// <param name="dates">the List of Dates to be sorted</param>
    void PerformSort(List<DateTime> dates);
}