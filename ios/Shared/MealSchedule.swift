import Foundation

// Compiled into both the iPhone app and the Apple Watch app.

/// Weekday serving hours used for the Today status pills and the Week tab's meal picker.
/// Source: 2026학년도 2학기 학내식당 운영 안내 (2026-08-31 ~ 12-11), 향설생활관1 and 교직원식당.
/// Update these when the next semester's notice comes out.
enum MealSchedule {
    enum Status {
        case upcoming, open, closed

        var label: String {
            switch self {
            case .upcoming: "운영 예정"
            case .open: "운영 중"
            case .closed: "운영 종료"
            }
        }
    }

    private static let hours: [String: (start: Int, end: Int)] = [
        "조식": (8 * 60, 10 * 60),
        "중식": (11 * 60, 14 * 60),
        "석식": (17 * 60, 18 * 60 + 30)
    ]

    /// "07:30–09:00", or nil for a meal without known hours.
    static func hoursLabel(for meal: String) -> String? {
        hours[meal].map { "\(clock($0.start))–\(clock($0.end))" }
    }

    /// Closed on weekends. At 향설생활관 lunch service stops at 13:30 and Friday dinner at 18:00,
    /// earlier than the posted hours.
    static func status(of meal: String, at date: Date, cafeteria: String = "",
                       calendar: Calendar = .current) -> Status? {
        guard let range = hours[meal] else { return nil }
        let weekday = calendar.component(.weekday, from: date)
        guard (2...6).contains(weekday) else { return .closed }

        var end = range.end
        if cafeteria.contains("향설") {
            if meal == "중식" { end = 13 * 60 + 30 }
            if meal == "석식", weekday == 6 { end = 18 * 60 }
        }
        let minute = minuteOfDay(date, calendar: calendar)
        if minute < range.start { return .upcoming }
        if minute < end { return .open }
        return .closed
    }

    /// The meal to highlight: the one being served now, otherwise the next one today.
    static func focusMeal(among meals: [String], at date: Date, cafeteria: String = "",
                          calendar: Calendar = .current) -> String? {
        meals.first { status(of: $0, at: date, cafeteria: cafeteria, calendar: calendar) == .open }
            ?? meals.first { status(of: $0, at: date, cafeteria: cafeteria, calendar: calendar) == .upcoming }
    }

    private static func minuteOfDay(_ date: Date, calendar: Calendar) -> Int {
        let parts = calendar.dateComponents([.hour, .minute], from: date)
        return (parts.hour ?? 0) * 60 + (parts.minute ?? 0)
    }

    private static func clock(_ minutes: Int) -> String {
        String(format: "%02d:%02d", minutes / 60, minutes % 60)
    }
}
