import SwiftUI
import UIKit

// MARK: - Colors

/// Palette from the "Today's Menu" design, with dark-mode counterparts.
enum Theme {
    /// Overall text scale; 80% was picked on a device as the best fit.
    static let uiScale: CGFloat = 0.8
    /// The design's accent (#D23312 at 64% opacity) flattened onto white, so it reads the same on every surface.
    static let accent = Color(light: 0xE27D68, dark: 0xE8866F)
    static let background = Color(light: 0xF4F5F7, dark: 0x0F1114)
    static let surface = Color(light: 0xFFFFFF, dark: 0x1B1E23)
    static let text = Color(light: 0x16181D, dark: 0xF2F3F5)
    static let secondaryText = Color(light: 0x555B68, dark: 0xA3A9B5)
    static let tertiaryText = Color(light: 0x3B414D, dark: 0xC5CAD3)
    static let border = Color(light: 0xE2E4E9, dark: 0x2C3038)
    static let divider = Color(light: 0xEEF0F3, dark: 0x262A31)
    /// Segmented-control tracks and the "closed" status pill.
    static let track = Color(light: 0xEDEFF2, dark: 0x2A2E35)
    /// Side-dish chips.
    static let chip = Color(light: 0xF4F5F7, dark: 0x262A31)
    static let dashedBorder = Color(light: 0xC4C8D0, dark: 0x3A3F48)
}

extension Color {
    init(light: UInt32, dark: UInt32) {
        self.init(uiColor: UIColor { traits in
            UIColor(hex: traits.userInterfaceStyle == .dark ? dark : light)
        })
    }
}

extension UIColor {
    convenience init(hex: UInt32) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: 1
        )
    }
}

// MARK: - Type

/// The menu text size chosen in Settings.
enum MenuTextSize: Int, CaseIterable, Identifiable {
    // Raw values are stored in UserDefaults; `normal` and `large` keep their old values.
    case small = -1, normal = 0, large = 1

    var id: Int { rawValue }

    var label: String {
        switch self {
        case .small: "작게"
        case .normal: "보통"
        case .large: "크게"
        }
    }

    /// Relative to the design's base sizes (16 → 19 → 22 pt for a dish name).
    var scale: CGFloat {
        switch self {
        case .small: 16.0 / 19.0
        case .normal: 1
        case .large: 22.0 / 19.0
        }
    }
}

private struct MenuTextScaleKey: EnvironmentKey {
    static let defaultValue: CGFloat = 1
}

extension EnvironmentValues {
    var menuTextScale: CGFloat {
        get { self[MenuTextScaleKey.self] }
        set { self[MenuTextScaleKey.self] = newValue }
    }
}

private struct UIScaleKey: EnvironmentKey {
    static let defaultValue: CGFloat = 1
}

extension EnvironmentValues {
    /// Overall text scale from the (temporary) slider in Settings.
    var uiScale: CGFloat {
        get { self[UIScaleKey.self] }
        set { self[UIScaleKey.self] = newValue }
    }
}

/// A design point size that still follows Dynamic Type; menu text also follows the in-app size setting.
private struct AppFont: ViewModifier {
    let size: CGFloat
    let weight: Font.Weight
    let isMenuText: Bool

    @Environment(\.menuTextScale) private var menuScale
    @Environment(\.uiScale) private var uiScale
    @ScaledMetric(relativeTo: .body) private var dynamicScale: CGFloat = 1

    func body(content: Content) -> some View {
        content.font(.system(size: size * dynamicScale * uiScale * (isMenuText ? menuScale : 1), weight: weight))
    }
}

extension View {
    func appFont(_ size: CGFloat, _ weight: Font.Weight = .regular, menu: Bool = false) -> some View {
        modifier(AppFont(size: size, weight: weight, isMenuText: menu))
    }
}

// MARK: - Meal hours

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

// MARK: - Dates

enum KoreanDate {
    private static let locale = Locale(identifier: "ko_KR")

    /// "10월 21일 수요일"
    static func full(_ date: Date) -> String {
        date.formatted(.dateTime.month(.wide).day().weekday(.wide).locale(locale))
    }

    /// "10월 19일 – 10월 23일" for the days a post covers.
    static func range(of days: [MenuDay]) -> String? {
        let sorted = days.sorted { $0.id < $1.id }
        guard let first = sorted.first, let last = sorted.last else { return nil }
        let start = "\(first.month)월 \(first.day)일"
        return first.id == last.id ? start : "\(start) – \(last.month)월 \(last.day)일"
    }
}

// MARK: - Layout helpers

/// Wraps its children onto new lines, like the design's side-dish chips.
struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = arrange(subviews, width: proposal.width ?? .infinity)
        let height = rows.last.map { $0.y + $0.height } ?? 0
        let width = rows.map(\.width).max() ?? 0
        return CGSize(width: proposal.width ?? width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        for row in arrange(subviews, width: bounds.width) {
            var x = bounds.minX
            for index in row.indices {
                let size = subviews[index].sizeThatFits(.unspecified)
                subviews[index].place(at: CGPoint(x: x, y: bounds.minY + row.y), proposal: ProposedViewSize(size))
                x += size.width + spacing
            }
        }
    }

    private struct Row {
        var indices: [Int] = []
        var y: CGFloat = 0
        var width: CGFloat = 0
        var height: CGFloat = 0
    }

    private func arrange(_ subviews: Subviews, width maxWidth: CGFloat) -> [Row] {
        var rows: [Row] = []
        var current = Row()
        for index in subviews.indices {
            let size = subviews[index].sizeThatFits(.unspecified)
            let proposedWidth = current.indices.isEmpty ? size.width : current.width + spacing + size.width
            if proposedWidth > maxWidth, !current.indices.isEmpty {
                rows.append(current)
                current = Row(y: current.y + current.height + spacing)
            }
            current.width = current.indices.isEmpty ? size.width : current.width + spacing + size.width
            current.height = max(current.height, size.height)
            current.indices.append(index)
        }
        if !current.indices.isEmpty { rows.append(current) }
        return rows
    }
}

/// Lays children out in equal-width columns (row-major order) and gives every cell
/// the height of the tallest one, so rows line up across columns and all match.
struct EqualHeightGrid: Layout {
    var columns: Int = 1
    var rowSpacing: CGFloat = 10
    var columnSpacing: CGFloat = 24

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.replacingUnspecifiedDimensions().width
        let cellWidth = columnWidth(in: width)
        let height = cellHeight(subviews, width: cellWidth)
        let rows = (subviews.count + columns - 1) / max(columns, 1)
        guard rows > 0 else { return CGSize(width: width, height: 0) }
        return CGSize(width: width, height: CGFloat(rows) * height + CGFloat(rows - 1) * rowSpacing)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let cellWidth = columnWidth(in: bounds.width)
        let height = cellHeight(subviews, width: cellWidth)
        for (index, subview) in subviews.enumerated() {
            let row = index / columns, column = index % columns
            subview.place(
                at: CGPoint(x: bounds.minX + CGFloat(column) * (cellWidth + columnSpacing),
                            y: bounds.minY + CGFloat(row) * (height + rowSpacing)),
                proposal: ProposedViewSize(width: cellWidth, height: height)
            )
        }
    }

    private func columnWidth(in width: CGFloat) -> CGFloat {
        let count = CGFloat(max(columns, 1))
        return max(0, (width - columnSpacing * (count - 1)) / count)
    }

    private func cellHeight(_ subviews: Subviews, width: CGFloat) -> CGFloat {
        subviews.map { $0.sizeThatFits(ProposedViewSize(width: width, height: nil)).height }.max() ?? 0
    }
}

/// A rounded card on the design's surface colour with a hairline (or highlighted) border.
struct CardBackground: ViewModifier {
    var isHighlighted = false
    var radius: CGFloat = 16

    func body(content: Content) -> some View {
        let shape = RoundedRectangle(cornerRadius: radius, style: .continuous)
        content
            .background(Theme.surface, in: shape)
            .overlay {
                shape.strokeBorder(isHighlighted ? Theme.accent : Theme.border, lineWidth: isHighlighted ? 2 : 1)
            }
    }
}

extension View {
    func card(highlighted: Bool = false, radius: CGFloat = 16) -> some View {
        modifier(CardBackground(isHighlighted: highlighted, radius: radius))
    }
}
