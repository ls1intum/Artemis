import Foundation

public class Policy {
	private var context: Context!

	init(_ context: Context!) {
		self.context = context
	}

	public func configure() {
		if self.context.getDates().count > 10 {
			print("More than 10 dates, choosing merge sort!")
			self.context.setSortAlgorithm(MergeSort())
		} else {
			print("Less or equal than 10 dates. choosing quick sort!")
			self.context.setSortAlgorithm(BubbleSort())
		}
	}
}