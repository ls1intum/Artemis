//
//  Context.swift
//  ${appName}
//

import Foundation

/*
 Task 2.2:
 Implement the Context Class
*/
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
        self.dates = sortAlgorithm.performSort(self.dates)
    }
    
    public func setRandomDates() {
        self.dates = self.createRandomDatesList()
    }
    
    /// Generates an Array of random Date objects with random Array size between 5 and 15.
    private func createRandomDatesList() -> [Date] {
        let listLength: Int = self.randomIntegerWithin(5, 15)
        var list = [Date]()
        let dateFormat = DateFormatter()
        dateFormat.dateFormat = "dd.MM.yyyy"
        dateFormat.timeZone = TimeZone(identifier: "UTC")
        let lowestDate: Date! = dateFormat.date(from: "15.07.2021")
        let highestDate: Date! = dateFormat.date(from: "13.02.2022")
        for _ in 0 ..< listLength {
            let randomDate: Date! = self.randomDateWithin(lowestDate, highestDate)
            list.append(randomDate)
        }
        return list
    }
    
    /// Creates a random Date within given range
    private func randomDateWithin(_ low: Date, _ high: Date) -> Date {
        let randomSeconds: Double = self.randomDoubleWithin(low.timeIntervalSince1970, high.timeIntervalSince1970)
        return Date(timeIntervalSince1970: randomSeconds)
    }
    
    /// Creates a random Double within given range
    private func randomDoubleWithin(_ low: Double, _ high: Double) -> Double {
        return Double.random(in: low...high)
    }
    
    /// Creates a random Integer within given range
    private func randomIntegerWithin(_ low: Int, _ high: Int) -> Int {
        return Int.random(in: low...high)
    }
}
