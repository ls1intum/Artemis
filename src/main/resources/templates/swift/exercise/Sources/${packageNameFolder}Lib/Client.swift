import Foundation

public class Client {
    /// TODO: Implement BubbleSort
    /// TODO: Implement MergeSort

    /// TODO: Create a SortStrategy interface according to the UML class diagram
    /// TODO: Make the sorting algorithms implement this interface.

    /// TODO: Create and implement a Context class according to the UML class diagram
    /// TODO: Create and implement a Policy class as described in the problem statement

    /**
     Main method.
     Add code to demonstrate your implementation here.
    */
    public static func main() {
        // TODO: Init Context and Policy

        //  Run 10 times to simulate different sorting strategies
        for _ in 0 ..< 10 {
            let dates: [Date] = createRandomDatesList()

            // TODO: Configure context

            print("Unsorted Array of course dates = ")
            printDateList(dates)

            // TODO: Sort dates

            print("Sorted Array of course dates = ")
            printDateList(dates)
        }
    }

    /// Generates an Array of random Date objects with random Array size between 5 and 15.
    private static func createRandomDatesList() -> [Date] {
        let listLength: Int = randomIntegerWithin(5, 15)
        var list = [Date]()

        let dateFormat = DateFormatter()
        dateFormat.dateFormat = "dd.MM.yyyy"
        let lowestDate: Date! = dateFormat.date(from: "08.11.2016")
        let highestDate: Date! = dateFormat.date(from: "15.04.2017")

        for _ in 0 ..< listLength {
            let randomDate: Date! = randomDateWithin(lowestDate, highestDate)
            list.append(randomDate)
        }
        return list
    }

    /// Creates a random Date within given Range
    private static func randomDateWithin(_ low: Date, _ high: Date) -> Date {
        let randomSeconds: Double = randomDoubleWithin(low.timeIntervalSince1970, high.timeIntervalSince1970)
        return Date(timeIntervalSince1970: randomSeconds)
    }

    /// Creates a random Double within given Range
    private static func randomDoubleWithin(_ low: Double, _ high: Double) -> Double {
        return Double.random(in: low...high)
    }

    /// Creates a random Integer within given Range
    private static func randomIntegerWithin(_ low: Int, _ high: Int) -> Int {
        return Int.random(in: low...high)
    }

    /// Prints out given Array of Date objects
    private static func printDateList(_ list: [Date]) {
        print(list)
    }
}
