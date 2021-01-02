import Foundation

public class Context {
    private var sortAlgorithm: SortStrategy!
    private var dates: [Date]!

    public func getDates() -> [Date] {
        return dates
    }

    public func setDates(_ dates: [Date]) {
        self.dates = dates
    }

    public func setSortAlgorithm(_ sa: SortStrategy) {
        sortAlgorithm = sa
    }

    public func getSortAlgorithm() -> SortStrategy {
        return sortAlgorithm
    }

    /// Runs the configured sort algorithm.
    public func sort() {
        sortAlgorithm.performSort(&self.dates)
    }
}
