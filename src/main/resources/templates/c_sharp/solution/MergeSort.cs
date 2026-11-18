namespace assignment;

public class MergeSort : ISortStrategy
{

    /// <summary>
    /// Wrapper method for the real MergeSort algorithm.
    /// </summary>
    /// <param name="input">the List of Dates to be sorted</param>
    public void PerformSort(List<DateTime> input)
    {
        Mergesort(input, 0, input.Count - 1);
    }

    /// <summary>
    /// Recursive merge sort method
    /// </summary>
    private void Mergesort(List<DateTime> input, int low, int high)
    {
        if (high - low < 1)
        {
            return;
        }

        int mid = (low + high) / 2;

        Mergesort(input, low, mid);
        Mergesort(input, mid + 1, high);
        Merge(input, low, mid, high);
    }

    /// <summary>
    /// Merge method
    /// </summary>
    private void Merge(List<DateTime> input, int low, int middle, int high)
    {
        DateTime[] temp = new DateTime[high - low + 1];

        int leftIndex = low;
        int rightIndex = middle + 1;
        int wholeIndex = 0;

        while (leftIndex <= middle && rightIndex <= high)
        {
            if (input[leftIndex] <= input[rightIndex])
            {
                temp[wholeIndex] = input[leftIndex++];
            }
            else
            {
                temp[wholeIndex] = input[rightIndex++];
            }
            wholeIndex++;
        }

        while (leftIndex <= middle)
        {
            temp[wholeIndex++] = input[leftIndex++];
        }
        while (rightIndex <= high)
        {
            temp[wholeIndex++] = input[rightIndex++];
        }

        for (wholeIndex = 0; wholeIndex < temp.Length; wholeIndex++)
        {
            input[wholeIndex + low] = temp[wholeIndex];
        }
    }
}
