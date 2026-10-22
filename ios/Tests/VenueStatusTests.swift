import XCTest

@testable import WeeklyMenu

final class VenueStatusTests: XCTestCase {
    private var calendar = Calendar(identifier: .gregorian)

    /// 2026-10-21 is a Wednesday; 10-23 a Friday; 10-24 a Saturday; 10-25 a Sunday.
    private func date(_ day: Int, _ hour: Int, _ minute: Int = 0) -> Date {
        calendar.date(from: DateComponents(year: 2026, month: 10, day: day, hour: hour, minute: minute))!
    }

    private func venue(_ name: String) throws -> Venue {
        try XCTUnwrap(VenueDirectory.groups.flatMap(\.venues).first { $0.name == name })
    }

    func testOpenClosingSoonAndClosedOnAWeekday() throws {
        let venue = try venue("크앙분식")
        XCTAssertEqual(venue.status(at: date(21, 12), calendar: calendar).level, .open)
        let soon = venue.status(at: date(21, 19, 45), calendar: calendar)
        XCTAssertEqual(soon.level, .closingSoon)
        XCTAssertEqual(soon.label, "15분 후 마감")
        XCTAssertEqual(venue.status(at: date(21, 8, 30), calendar: calendar).label, "09:00 오픈")
        XCTAssertEqual(venue.status(at: date(21, 20, 0), calendar: calendar).label, "운영 종료")
    }

    func testClosingSoonStartsThirtyMinutesBeforeClose() throws {
        let venue = try venue("크앙분식")
        XCTAssertEqual(venue.status(at: date(21, 19, 29), calendar: calendar).level, .open)
        XCTAssertEqual(venue.status(at: date(21, 19, 30), calendar: calendar).level, .closingSoon)
    }

    func testFridayClosesEarlier() throws {
        let venue = try venue("크앙분식")
        XCTAssertEqual(venue.status(at: date(23, 14, 40), calendar: calendar).level, .closingSoon)
        XCTAssertEqual(venue.status(at: date(23, 15, 10), calendar: calendar).level, .closed)
    }

    func testWeekendHours() throws {
        XCTAssertEqual(try venue("크앙분식").status(at: date(24, 12), calendar: calendar).label, "오늘 휴무")
        XCTAssertEqual(try venue("얼큰한 뼈해장국").status(at: date(24, 12), calendar: calendar).level, .open)
        XCTAssertEqual(try venue("또바기육개장").status(at: date(25, 12), calendar: calendar).level, .open)
        XCTAssertEqual(try venue("또바기육개장").status(at: date(25, 18, 45), calendar: calendar).level, .closed)
    }

    func testNonOperatingVenue() throws {
        let status = try venue("향설생활관3").status(at: date(21, 12), calendar: calendar)
        XCTAssertEqual(status.level, .closed)
        XCTAssertEqual(status.label, "미운영")
    }

    func testHyangseolMealCutoffsAndWeekends() {
        XCTAssertEqual(MealSchedule.status(of: "중식", at: date(21, 13, 15), cafeteria: "향설 1관", calendar: calendar), .open)
        XCTAssertEqual(MealSchedule.status(of: "중식", at: date(21, 13, 45), cafeteria: "향설 1관", calendar: calendar), .closed)
        XCTAssertEqual(MealSchedule.status(of: "중식", at: date(21, 13, 45), cafeteria: "교직원식당", calendar: calendar), .open)
        XCTAssertEqual(MealSchedule.status(of: "석식", at: date(23, 18, 15), cafeteria: "향설 1관", calendar: calendar), .closed)
        XCTAssertEqual(MealSchedule.status(of: "석식", at: date(21, 18, 15), cafeteria: "향설 1관", calendar: calendar), .open)
        XCTAssertEqual(MealSchedule.status(of: "중식", at: date(24, 12), cafeteria: "향설 1관", calendar: calendar), .closed)
    }
}

extension VenueStatusTests {
    func testHyangseolLunchStopsServingAtOneThirty() throws {
        let lunch = try XCTUnwrap(VenueDirectory.groups.first { $0.building == "향설생활관1" }?.venues.first { $0.name == "중식" })
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = .current
        func at(_ h: Int, _ m: Int) -> Date {
            calendar.date(from: DateComponents(year: 2026, month: 10, day: 21, hour: h, minute: m))!
        }
        XCTAssertEqual(lunch.status(at: at(13, 0), calendar: calendar).level, .closingSoon)
        XCTAssertEqual(lunch.status(at: at(13, 35), calendar: calendar).level, .closed)
        XCTAssertEqual(lunch.status(at: at(13, 35), calendar: calendar).hours, "11:00–14:00")
    }

    func testSavedBuildingOrderIsApplied() {
        let defaults = VenueDirectory.groups.map(\.building)
        XCTAssertEqual(VenueDirectory.groups(orderedBy: "").map(\.building), defaults)

        let order = VenueDirectory.groups(orderedBy: "학생회관,향설생활관1").map(\.building)
        XCTAssertEqual(Array(order.prefix(2)), ["학생회관", "향설생활관1"])
        XCTAssertEqual(Set(order), Set(defaults))
        XCTAssertEqual(order.count, defaults.count)

        // Names that no longer exist are ignored.
        XCTAssertEqual(VenueDirectory.groups(orderedBy: "없는건물").map(\.building), defaults)
    }
}
