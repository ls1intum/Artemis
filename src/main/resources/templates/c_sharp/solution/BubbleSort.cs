namespace assignment;

public class BubbleSort : ISortStrategy
{
    /// <summary>
    /// Sorts dates with BubbleSort.
    /// </summary>
    /// <param name="dates">the List of Dates to be sorted</param>
    public void PerformSort(List<DateTime> dates)
    {
        for (int i = dates.Count - 1; i >= 0; i--)
        {
            for (int j = 0; j < i; j++)
            {
                if (dates[j] > dates[j + 1])
                {
                    (dates[j + 1], dates[j]) = (dates[j], dates[j + 1]);
                }
            }
        }
    }
}