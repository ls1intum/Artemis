use std::any::Any;

pub trait SortStrategy<T: Ord>: Any {
    /// Sorts a slice of `T`s.
    ///
    /// Arguments:
    ///
    /// * `input`: slice of items to be sorted
    fn perform_sort(&self, input: &mut [T]);
}
