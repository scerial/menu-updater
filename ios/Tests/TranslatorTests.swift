import XCTest
@testable import WeeklyMenu

/// Mirrors the Android `TranslatorTest`: the same rules against a small hand-written dictionary.
final class TranslatorTests: XCTestCase {
    private let translator = Translator(data: TranslationData(
        dishes: ["콩나물국": "Bean Sprout Soup", "김치": "Kimchi", "딸기잼": "Strawberry Jam",
                 "샐러드": "Salad", "토스트": "Toast"],
        aliases: ["공나물국": "콩나물국"],
        glossary: ["돈육": "Pork", "김치": "Kimchi", "볶음": "Stir-fry", "고사리": "Bracken",
                   "무침": "^Seasoned", "볶음밥": "Fried Rice", "새우": "Shrimp"],
        places: ["향설1관": "Hyangseol Hall 1"],
        meals: ["중식": "Lunch"],
        weekdays: ["화": "Tue"]
    ))

    func testDictionaryAliasAndSpaces() {
        XCTAssertEqual(translator.dish("콩나물국"), "Bean Sprout Soup")
        XCTAssertEqual(translator.dish("공나물국"), "Bean Sprout Soup")
        XCTAssertEqual(translator.dish("딸기 잼"), "Strawberry Jam")
    }

    func testWordByWordGuess() {
        XCTAssertEqual(translator.dish("돈육김치볶음"), "Pork Kimchi Stir-fry")
        XCTAssertEqual(translator.dish("고사리무침"), "Seasoned Bracken")
        XCTAssertEqual(translator.dish("새우볶음밥"), "Shrimp Fried Rice")
    }

    func testUncoveredPartKeepsTheKorean() {
        XCTAssertEqual(translator.dish("교자민두"), "교자민두")
        XCTAssertEqual(translator.sources("교자민두"), [.none])
    }

    func testDishesRunTogetherAndSeparators() {
        XCTAssertEqual(translator.dish("김치샐러드"), "Kimchi & Salad")
        XCTAssertEqual(translator.dish("콩나물국 김치"), "Bean Sprout Soup & Kimchi")
        XCTAssertEqual(translator.dish("김치/샐러드/후식차"), "Kimchi / Salad / 후식차")
        XCTAssertEqual(translator.dish("김치*샐러드"), "Kimchi + Salad")
        XCTAssertEqual(translator.dish("토스트&딸기잼"), "Toast & Strawberry Jam")
    }

    func testPlacesMealsAndWeekdays() {
        XCTAssertEqual(translator.place("향설1관"), "Hyangseol Hall 1")
        XCTAssertEqual(translator.meal("중식"), "Lunch")
        XCTAssertEqual(translator.weekday("화"), "Tue")
        XCTAssertEqual(translator.place("교직원식당"), "교직원식당")
    }

    func testKoreanTrChangesNothing() {
        XCTAssertEqual(Tr.korean.dish("돈육김치볶음"), "돈육김치볶음")
        XCTAssertEqual(Tr.korean.meal("중식"), "중식")
    }

    func testBundledFileLoadsAndCoversCommonDishes() throws {
        let bundled = try XCTUnwrap(Translator.load(from: Bundle(for: MenuViewModel.self)))
        XCTAssertEqual(bundled.dish("깍두기"), "Cubed Radish Kimchi")
        XCTAssertEqual(bundled.dish("공나물무침"), "Seasoned Bean Sprouts")
        XCTAssertEqual(bundled.place("향설1관"), "Hyangseol Hall 1")
    }

    func testDates() {
        XCTAssertEqual(AppDate.range(from: 20260831, until: 20261211, english: true), "Aug 31 – Dec 11")
        XCTAssertEqual(AppDate.range(from: 20260831, until: 20261211, english: false), "8월 31일 – 12월 11일")
    }
}
