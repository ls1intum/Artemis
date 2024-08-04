use std::cmp::Ord;

use crate::sort_strategy::SortStrategy;

pub struct MergeSort;

impl<T: Ord> SortStrategy<T> for MergeSort {
    /// Sorts items with the Merge Sort algorithm.
    ///
    /// Arguments:
    ///
    /// * `input`: slice of items to be sorted
    fn perform_sort(&self, input: &mut [T]) {
        input.sort();
    }
}
