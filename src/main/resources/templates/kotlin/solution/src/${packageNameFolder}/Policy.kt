package ${packageName}

//TODO: add the missing constructor and members
class Policy/*<remove>*/(private val context: Context)/*</remove>*/ {

    //TODO: add `configure` method which accepts two boolean parameters
    /*<remove>*/
    fun configure(timeIsImportant: Boolean, spaceIsImportant: Boolean) {
        if (timeIsImportant && !spaceIsImportant) {
            println("Time is important –> Merge Sort!")
            context.sortAlgorithm = MergeSort()
        } else if (timeIsImportant && spaceIsImportant) {
            println("Time & Space are important –> Quick Sort!")
            context.sortAlgorithm = QuickSort()
        }
    }
    /*</remove>*/

}
