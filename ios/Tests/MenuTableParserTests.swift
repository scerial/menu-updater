import XCTest

@testable import WeeklyMenu

final class MenuTableParserTests: XCTestCase {
    /// Parses a fixture of "x y width height | text" rows into recognized lines.
    private func lines(from fixture: String) -> [RecognizedLine] {
        fixture.split(separator: "\n").compactMap { row in
            let parts = row.components(separatedBy: " | ")
            guard parts.count == 2 else { return nil }
            let numbers = parts[0].split(separator: " ").compactMap { Double($0) }
            guard numbers.count == 4 else { return nil }
            return RecognizedLine(
                text: parts[1],
                box: CGRect(x: numbers[0], y: numbers[1], width: numbers[2], height: numbers[3])
            )
        }
    }

    func testParsesCafeteriaNameAndFiveDays() throws {
        let table = try XCTUnwrap(MenuTableParser.parse(lines(from: OCRFixtures.hyangseol1)))

        XCTAssertEqual(table.cafeteria, "향설 1관")
        XCTAssertEqual(table.days.map(\.heading), [
            "10월 19일 (월)", "10월 20일 (화)", "10월 21일 (수)", "10월 22일 (목)", "10월 23일 (금)"
        ])
    }

    func testGroupsItemsByColumnAndMealInReadingOrder() throws {
        let table = try XCTUnwrap(MenuTableParser.parse(lines(from: OCRFixtures.hyangseol1)))
        let tuesday = try XCTUnwrap(table.days.first { $0.day == 20 })

        XCTAssertEqual(tuesday.meals.map(\.name), ["조식", "중식", "석식"])
        XCTAssertEqual(
            tuesday.meals[0].items,
            ["순대야채볶음", "군만두", "숙주무침", "청경채무침", "고추무침/된장국", "아산쌀밥/김치/샐러드"]
        )
        XCTAssertEqual(
            tuesday.meals[1].items,
            ["장각삼계탕", "비빔만두", "열무된정무침", "콩조림", "무말랭이무침", "김치/샐러드/후식차"]
        )
    }

    func testSharedRowIsAddedToEveryDayAndNotLostFromTheCenterColumn() throws {
        let table = try XCTUnwrap(MenuTableParser.parse(lines(from: OCRFixtures.hyangseol1)))

        for day in table.days {
            XCTAssertEqual(day.meals[0].items.last, "아산쌀밥/김치/샐러드", day.heading)
        }
        // Wednesday's own breakfast items must remain, with the shared row only once.
        let wednesday = try XCTUnwrap(table.days.first { $0.day == 21 })
        XCTAssertEqual(wednesday.meals[0].items.filter { $0 == "아산쌀밥/김치/샐러드" }.count, 1)
        XCTAssertTrue(wednesday.meals[0].items.contains("도시락김/초코머핀"))
    }

    func testExcludesTitleLogoAndOriginFooter() throws {
        let table = try XCTUnwrap(MenuTableParser.parse(lines(from: OCRFixtures.hyangseol1)))
        let everything = table.days.flatMap(\.meals).flatMap(\.items)

        XCTAssertFalse(everything.contains { $0.contains("원산지") || $0.contains("국내산") || $0.contains("상기메뉴") })
        XCTAssertFalse(everything.contains("SCH"))
        XCTAssertFalse(everything.contains("순천향대학교"))
    }

    func testSingleMealTableKeepsExtraCenterColumnItemSeparateFromSharedRow() throws {
        let table = try XCTUnwrap(MenuTableParser.parse(lines(from: OCRFixtures.facultyDining)))
        let wednesday = try XCTUnwrap(table.days.first { $0.day == 21 })

        XCTAssertEqual(table.cafeteria, "교직원식당")
        XCTAssertEqual(wednesday.meals.map(\.name), ["중식"])
        XCTAssertEqual(wednesday.meals[0].items.suffix(3), ["배추김치", "숭늉", "샐러드, 토스트&딸기잼"])
    }

    func testReturnsNilWhenNoDateHeadersAreRecognized() {
        let noHeaders = [RecognizedLine(text: "밥", box: CGRect(x: 0.2, y: 0.5, width: 0.05, height: 0.02))]
        XCTAssertNil(MenuTableParser.parse(noHeaders))
    }

    func testTodayEntriesMatchOnMonthAndDay() throws {
        let hyangseol = try XCTUnwrap(MenuTableParser.parse(lines(from: OCRFixtures.hyangseol1)))
        let faculty = try XCTUnwrap(MenuTableParser.parse(lines(from: OCRFixtures.facultyDining)))
        let menu = WeeklyMenu(
            title: "26년 10월 4주차 순천향대학교 식단",
            postedAt: "2026-10-16",
            sourceURL: URL(string: "https://www.agoodplacefnb.com/today")!,
            tables: [hyangseol, faculty]
        )

        let calendar = Calendar(identifier: .gregorian)
        let tuesday = try XCTUnwrap(calendar.date(from: DateComponents(year: 2026, month: 10, day: 20)))
        let entries = menu.entries(on: tuesday, calendar: calendar)
        XCTAssertEqual(entries.map(\.cafeteria), ["향설 1관", "교직원식당"])
        XCTAssertTrue(entries.allSatisfy { $0.day.day == 20 })

        let nextWeek = try XCTUnwrap(calendar.date(from: DateComponents(year: 2026, month: 10, day: 27)))
        XCTAssertTrue(menu.entries(on: nextWeek, calendar: calendar).isEmpty)
    }

    /// 의대식당's origin block starts on a row above its "원산지" label; it must not become a menu item.
    func testOriginRowsAboveTheLabelAreNotMenuItems() throws {
        func line(_ text: String, x: CGFloat, y: CGFloat, w: CGFloat = 0.12) -> RecognizedLine {
            RecognizedLine(text: text, box: CGRect(x: x, y: y, width: w, height: 0.03))
        }
        let table = try XCTUnwrap(MenuTableParser.parse([
            line("순천향의대식당 주간메뉴표", x: 0.3, y: 0.85, w: 0.4),
            line("10월20일(화)", x: 0.30, y: 0.72),
            line("10월21일(수)", x: 0.50, y: 0.72),
            line("중식", x: 0.08, y: 0.55, w: 0.04),
            line("사태김치찜", x: 0.30, y: 0.65),
            line("깍두기볶음밥", x: 0.50, y: 0.65),
            line("김치", x: 0.30, y: 0.45),
            line("김치", x: 0.50, y: 0.45),
            line("쌀:국내산/ 돈육:국내산/ 도토리묵:중국산", x: 0.15, y: 0.34, w: 0.7),
            line("잡곡류:국내산,미국산/ 돈까스:국내산", x: 0.15, y: 0.30, w: 0.7),
            line("원산지", x: 0.05, y: 0.32, w: 0.08)
        ]))

        let items = table.days.flatMap { $0.meals.flatMap(\.items) }
        XCTAssertFalse(items.contains { $0.contains("국내산") })
        XCTAssertEqual(table.days.first?.meals.first?.items, ["사태김치찜", "김치"])
    }
}
