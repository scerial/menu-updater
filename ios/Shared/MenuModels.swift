import Foundation

// Menu data model, compiled into both the iPhone app and the Apple Watch app.

struct MealSection: Codable, Equatable, Identifiable {
    let name: String
    let items: [String]

    var id: String { name }
}

struct MenuDay: Codable, Equatable, Identifiable {
    let month: Int
    let day: Int
    let weekday: String
    let meals: [MealSection]

    var id: Int { month * 100 + day }
    var heading: String { "\(month)월 \(day)일 (\(weekday))" }
}

struct MenuTable: Codable, Equatable, Identifiable {
    let cafeteria: String
    let days: [MenuDay]

    var id: String { cafeteria }
}

/// A board post a menu was read from.
struct MenuSource: Codable, Equatable {
    let title: String
    let postedAt: String
    let url: URL
}

struct WeeklyMenu: Codable, Equatable {
    let title: String
    let postedAt: String
    let sourceURL: URL
    let tables: [MenuTable]
    /// Further posts merged into this menu (the 의과대학 post). Optional so older cached menus still load.
    var additionalSources: [MenuSource]? = nil

    /// Every post this menu came from, main post first.
    var sources: [MenuSource] {
        [MenuSource(title: title, postedAt: postedAt, url: sourceURL)] + (additionalSources ?? [])
    }

    /// Each cafeteria's menu for the given date, matched on the month/day printed in the column headers.
    func entries(on date: Date = Date(), calendar: Calendar = .current) -> [(cafeteria: String, day: MenuDay)] {
        let components = calendar.dateComponents([.month, .day], from: date)
        return tables.compactMap { table in
            table.days.first { $0.month == components.month && $0.day == components.day }
                .map { (cafeteria: table.cafeteria, day: $0) }
        }
    }
}
