namespace assignment;

public class Context
{
    private ISortStrategy? sortAlgorithm;
    private List<DateTime>? dates;

    public ISortStrategy? SortAlgorithm { get => sortAlgorithm; set => sortAlgorithm = value; }
    public List<DateTime>? Dates { get => dates; set => dates = value; }

    /// <summary>
    /// Runs the configured sort algorithm.
    /// </summary>
    public void Sort()
    {
        if (sortAlgorithm == null || dates == null)
        {
            throw new InvalidOperationException("SortAlgorithm and Dates have to be set before Sort() can be called");
        }

        sortAlgorithm.PerformSort(dates);
    }
}
