import Foundation

public protocol SortStrategy {
    func performSort(_ input: inout [Date])
}
