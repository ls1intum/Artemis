import Foundation

public protocol SortStrategy {
    /**
      Sorts a list of Dates.

      - Parameter input: list of Dates
     */
    func performSort(_ input: [Date]) -> [Date]
}
