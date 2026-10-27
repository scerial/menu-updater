import SwiftUI

// MARK: - Data

/// One restaurant from the 학내식당 운영 안내. Times are minutes after midnight.
struct Venue: Identifiable, Equatable {
    let building: String
    let name: String
    /// Monday to Friday; nil when closed on weekdays.
    var weekday: Range<Int>?
    /// Fridays close earlier at some venues.
    var fridayEnd: Int?
    var saturday: Range<Int>?
    var sunday: Range<Int>?
    /// Last serving time when it is earlier than the posted closing time (향설 lunch: 13:30).
    var serviceEnd: Int?
    /// False for venues the notice lists as 미운영 (not operating).
    var isOperating = true

    var id: String { building + name }

    /// Today's opening hours, or nil on a closed day.
    func hours(on date: Date, calendar: Calendar = .current) -> Range<Int>? {
        switch calendar.component(.weekday, from: date) {
        case 1: return sunday
        case 7: return saturday
        case 6:
            guard let weekday else { return nil }
            return weekday.lowerBound..<min(fridayEnd ?? weekday.upperBound, weekday.upperBound)
        default: return weekday
        }
    }

    func status(at date: Date, calendar: Calendar = .current) -> VenueStatus {
        guard isOperating else { return VenueStatus(level: .closed, label: "미운영", hours: nil, kind: .notOperating) }
        guard let range = hours(on: date, calendar: calendar) else {
            return VenueStatus(level: .closed, label: "오늘 휴무", hours: nil, kind: .dayOff)
        }
        let parts = calendar.dateComponents([.hour, .minute], from: date)
        let minute = (parts.hour ?? 0) * 60 + (parts.minute ?? 0)
        let hoursText = "\(Venue.clock(range.lowerBound))–\(Venue.clock(range.upperBound))"
        let end = min(range.upperBound, serviceEnd ?? range.upperBound)

        if minute < range.lowerBound {
            return VenueStatus(level: .closed, label: "\(Venue.clock(range.lowerBound)) 오픈", hours: hoursText,
                               kind: .opensAt, opensAt: Venue.clock(range.lowerBound))
        }
        if minute >= end {
            return VenueStatus(level: .closed, label: "운영 종료", hours: hoursText, kind: .ended)
        }
        let remaining = end - minute
        if remaining <= Venue.closingSoonMinutes {
            return VenueStatus(level: .closingSoon, label: "\(remaining)분 후 마감", hours: hoursText,
                               kind: .closingSoon, minutesLeft: remaining)
        }
        return VenueStatus(level: .open, label: "운영 중", hours: hoursText, kind: .open)
    }

    static let closingSoonMinutes = 30

    static func clock(_ minutes: Int) -> String {
        String(format: "%02d:%02d", minutes / 60, minutes % 60)
    }
}

/// A venue's live status. `label` is the Korean text; views use `displayLabel`, built from `kind`
/// (with `opensAt` or `minutesLeft`), so the status can be shown in English too.
struct VenueStatus: Equatable {
    enum Level { case open, closingSoon, closed }
    enum Kind { case notOperating, dayOff, opensAt, ended, closingSoon, open }

    let level: Level
    let label: String
    let hours: String?
    let kind: Kind
    var opensAt: String?
    var minutesLeft: Int?
}

/// Source: 2026학년도 2학기 학내식당 운영 안내 (2026-08-31 ~ 12-11).
enum VenueDirectory {
    /// First and last day the notice covers, as yyyymmdd.
    static let validFrom = 20260831
    static let validUntil = 20261211

    static func isWithinNotice(_ date: Date, calendar: Calendar = .current) -> Bool {
        let parts = calendar.dateComponents([.year, .month, .day], from: date)
        let value = (parts.year ?? 0) * 10000 + (parts.month ?? 0) * 100 + (parts.day ?? 0)
        return (validFrom...validUntil).contains(value)
    }

    private static func t(_ hour: Int, _ minute: Int = 0) -> Int { hour * 60 + minute }

    typealias Group = (building: String, venues: [Venue])

    /// `groups` in the user's saved building order (comma-separated names). Buildings missing
    /// from the saved order keep their default position at the end.
    static func groups(orderedBy saved: String) -> [Group] {
        let names = saved.split(separator: ",").map(String.init)
        let ranked = names.compactMap { name in groups.first { $0.building == name } }
        return ranked + groups.filter { group in !names.contains(group.building) }
    }

    static let groups: [Group] = [
        ("향설생활관1", [
            Venue(building: "향설생활관1", name: "조식", weekday: t(8)..<t(10)),
            Venue(building: "향설생활관1", name: "중식", weekday: t(11)..<t(14), serviceEnd: t(13, 30)),
            Venue(building: "향설생활관1", name: "석식", weekday: t(17)..<t(18, 30), fridayEnd: t(18))
        ]),
        ("향설생활관2", [
            Venue(building: "향설생활관2", name: "크앙분식", weekday: t(9)..<t(20), fridayEnd: t(15)),
            Venue(building: "향설생활관2", name: "얼큰한 뼈해장국", weekday: t(9)..<t(20), fridayEnd: t(19),
                  saturday: t(9)..<t(14)),
            Venue(building: "향설생활관2", name: "또바기육개장", weekday: t(9)..<t(19, 30),
                  saturday: t(11)..<t(15, 30), sunday: t(11)..<t(18, 30)),
            Venue(building: "향설생활관2", name: "탕탕칼제비", weekday: t(9)..<t(19, 30))
        ]),
        ("향설생활관3", [
            Venue(building: "향설생활관3", name: "향설생활관3", isOperating: false)
        ]),
        ("학생회관", [
            Venue(building: "학생회관", name: "대한짜장 (중식)", weekday: t(9)..<t(19), fridayEnd: t(17)),
            Venue(building: "학생회관", name: "수오미엔 (쌀국수)", weekday: t(10)..<t(19, 30)),
            Venue(building: "학생회관", name: "하즈벤 (돈까스)", weekday: t(9)..<t(19), fridayEnd: t(14)),
            Venue(building: "학생회관", name: "공여사맛집 (덮밥)", weekday: t(8)..<t(19), fridayEnd: t(16))
        ]),
        ("교직원식당", [
            Venue(building: "교직원식당", name: "중식", weekday: t(11)..<t(14))
        ]),
        ("순천향의대식당", [
            Venue(building: "순천향의대식당", name: "중식", weekday: t(11)..<t(14))
        ])
    ]
}

// MARK: - View

extension VenueStatus.Level {
    var color: Color {
        switch self {
        case .open: Color(light: 0x2FA84F, dark: 0x4CC46B)
        case .closingSoon: Color(light: 0xE0A100, dark: 0xF2C230)
        case .closed: Color(light: 0xB8BDC7, dark: 0x555B66)
        }
    }
}

/// Live open/closing-soon/closed list for every campus restaurant in the notice.
struct VenuePanel: View {
    let groups: [VenueDirectory.Group]
    /// Two columns on wide screens: the first three buildings on the left, the rest on the right.
    var usesColumns = false
    var isEditing = false
    /// Moves a building up (-1) or down (+1) in the saved order.
    var move: (String, Int) -> Void = { _, _ in }

    var body: some View {
        // Re-evaluate every 30 seconds so dots change while the app is open.
        TimelineView(.periodic(from: .now, by: 30)) { context in
            let now = context.date
            VStack(alignment: .leading, spacing: 8) {
                if usesColumns {
                    HStack(alignment: .top, spacing: 24) {
                        ForEach(columns.indices, id: \.self) { index in
                            VenueCard(groups: columns[index], now: now, isEditing: isEditing,
                                      firstBuilding: groups.first?.building, lastBuilding: groups.last?.building,
                                      move: move)
                                .frame(maxWidth: .infinity, alignment: .top)
                        }
                    }
                } else {
                    VenueCard(groups: groups, now: now, isEditing: isEditing,
                              firstBuilding: groups.first?.building, lastBuilding: groups.last?.building,
                              move: move)
                }

                Text(footer(at: now))
                    .appFont(13)
                    .foregroundStyle(Theme.secondaryText)
                    .padding(.horizontal, 4)
            }
        }
    }

    private var columns: [[VenueDirectory.Group]] {
        let split = min(3, groups.count)
        return [Array(groups[..<split]), Array(groups[split...])]
    }

    private func footer(at date: Date) -> String {
        let text = String(localized: "2026학년도 2학기 운영 안내(8.31–12.11) 기준입니다. 이용률에 따라 일부 바뀔 수 있습니다. 문의: 학생지원팀 041-530-1033")
        if VenueDirectory.isWithinNotice(date) { return text }
        return String(localized: "지금은 이 안내가 다루는 기간이 아니어서 시간이 다를 수 있습니다. \(text)")
    }
}

struct VenueRow: View {
    let venue: Venue
    let status: VenueStatus
    @Environment(\.tr) private var tr

    var body: some View {
        let isClosed = status.level == .closed

        HStack(spacing: 12) {
            Circle()
                .fill(status.level.color)
                .frame(width: 10, height: 10)
                .accessibilityHidden(true)
            Text(tr.venue(venue.name))
                .appFont(17, isClosed ? .regular : .bold)
                .foregroundStyle(isClosed ? Theme.secondaryText : Theme.text)
            Spacer(minLength: 8)
            VStack(alignment: .trailing, spacing: 0) {
                Text(status.displayLabel)
                    .appFont(15, .bold)
                    .foregroundStyle(isClosed ? Theme.secondaryText : Theme.text)
                if let hours = status.hours {
                    Text(hours)
                        .appFont(13)
                        .foregroundStyle(Theme.secondaryText)
                }
            }
        }
        .padding(.horizontal, 16)
        .frame(minHeight: 52)
        .opacity(isClosed ? 0.7 : 1)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: "\(tr.venue(venue.name)), \(status.displayLabel)\(status.hours.map { ", \($0)" } ?? "")"))
    }
}

/// The 운영 시간 tab: every restaurant with a live status dot and today's hours.
struct HoursPage: View {
    let layout: ScreenLayout
    /// Building names in the user's order, comma-separated; empty means the default order.
    @AppStorage("venue-building-order") private var savedOrder = ""
    @State private var isEditing = false
    @Environment(\.tr) private var tr

    var body: some View {
        let groups = VenueDirectory.groups(orderedBy: savedOrder)
        let notice = AppDate.range(from: VenueDirectory.validFrom, until: VenueDirectory.validUntil, english: tr.isEnglish)

        VStack(spacing: 0) {
            ScreenHeader(subtitle: "\(String(localized: "2026학년도 2학기")) · \(notice)", title: String(localized: "운영 시간"), layout: layout,
                         trailing: AnyView(editControls)) { EmptyView() }

            ScrollView {
                VenuePanel(groups: groups, usesColumns: layout.showsColumns, isEditing: isEditing) { building, offset in
                    moveBuilding(building, by: offset, in: groups)
                }
                    .padding(.horizontal, layout.horizontalPadding)
                    .padding(.top, 20)
                    .padding(.bottom, 28)
                    .frame(maxWidth: layout.showsColumns ? 1100 : 720, alignment: .leading)
                    .frame(maxWidth: .infinity, alignment: layout.isWide ? .leading : .center)
            }
        }
    }
}

extension HoursPage {
    private var editControls: some View {
        HStack(spacing: 12) {
            Button(isEditing ? "완료" : "순서 편집") {
                withAnimation(.snappy) { isEditing.toggle() }
            }
            .appFont(16, .bold)
            .foregroundStyle(Theme.accent)
            if isEditing, !savedOrder.isEmpty {
                Button("기본 순서로") {
                    withAnimation(.snappy) { savedOrder = "" }
                }
                .appFont(16)
                .foregroundStyle(Theme.secondaryText)
            }
        }
        .padding(.bottom, 2)
    }

    private func moveBuilding(_ building: String, by offset: Int, in groups: [VenueDirectory.Group]) {
        var names = groups.map(\.building)
        guard let from = names.firstIndex(of: building) else { return }
        let to = from + offset
        guard names.indices.contains(to) else { return }
        names.swapAt(from, to)
        withAnimation(.snappy) { savedOrder = names.joined(separator: ",") }
    }
}

struct VenueCard: View {
    let groups: [VenueDirectory.Group]
    let now: Date
    var isEditing = false
    var firstBuilding: String?
    var lastBuilding: String?
    var move: (String, Int) -> Void = { _, _ in }
    @Environment(\.tr) private var tr

    var body: some View {
        VStack(spacing: 0) {
            ForEach(Array(groups.enumerated()), id: \.element.building) { index, group in
                if index > 0 { SettingsDivider() }
                HStack {
                    Text(tr.place(group.building))
                        .appFont(14, .bold)
                        .foregroundStyle(Theme.secondaryText)
                    Spacer()
                    if isEditing {
                        moveButton("chevron.up", String(localized: "위로"), group.building, -1, disabled: group.building == firstBuilding)
                        moveButton("chevron.down", String(localized: "아래로"), group.building, 1, disabled: group.building == lastBuilding)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)
                .padding(.bottom, 2)
                ForEach(group.venues) { venue in
                    VenueRow(venue: venue, status: venue.status(at: now))
                }
            }
        }
        .padding(.bottom, 6)
        .card()
    }

    private func moveButton(_ symbol: String, _ label: String, _ building: String, _ offset: Int,
                            disabled: Bool) -> some View {
        Button { move(building, offset) } label: {
            Image(systemName: symbol)
                .font(.system(size: 15, weight: .bold))
                .foregroundStyle(disabled ? Theme.secondaryText.opacity(0.4) : Theme.accent)
                .frame(width: 44, height: 36)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(disabled)
        .accessibilityLabel(String(localized: "\(tr.place(building)) \(label) 이동"))
    }
}
