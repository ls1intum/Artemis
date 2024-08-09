use crate::sort_strategy::SortStrategy;
use chrono::NaiveDate;
use std::cell::{Ref, RefCell};

pub struct Context {
    sort_algorithm: RefCell<Option<Box<dyn SortStrategy<NaiveDate>>>>,
}

impl Context {
    pub fn new() -> Context {
        Context {
            sort_algorithm: RefCell::new(None),
        }
    }

    /// Runs the configured sorting algorithm.
    pub fn sort(&self, data: &mut [NaiveDate]) {
        let sort_algorithm = self.sort_algorithm.borrow();
        let sort_algorithm = sort_algorithm
            .as_ref()
            .expect("sort_algorithm has to be set before sort() is called");

        sort_algorithm.perform_sort(data);
    }

    pub fn set_sort_algorithm(&self, sort_algorithm: Box<dyn SortStrategy<NaiveDate>>) {
        self.sort_algorithm.borrow_mut().replace(sort_algorithm);
    }

    pub fn sort_algorithm(&self) -> Ref<Option<Box<dyn SortStrategy<NaiveDate>>>> {
        self.sort_algorithm.borrow()
    }
}
