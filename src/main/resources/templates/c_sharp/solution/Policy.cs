namespace assignment;

public class Policy
{
    private const int DATES_SIZE_THRESHOLD = 10;

    private Context context;

    public Policy(Context context)
    {
        this.context = context;
    }

    public Context Context { get => context; set => context = value; }

    /// <summary>
    /// Chooses a strategy depending on the number of date objects.
    /// </summary>
    public void Configure()
    {
        if (this.context.Dates == null)
        {
            throw new InvalidOperationException("Dates of Context has to be set before Configure() can be called");
        }

        if (this.context.Dates.Count > DATES_SIZE_THRESHOLD)
        {
            this.context.SortAlgorithm = new MergeSort();
        }
        else
        {
            this.context.SortAlgorithm = new BubbleSort();
        }
    }
}
