package com.menuupdater.weeklymenu.data

/** A board post that may hold a weekly menu. */
data class MenuPost(val title: String, val date: String, val url: String)

/** The user-facing failure reasons for a menu load; messages match the iOS `MenuError`. */
enum class MenuError(val displayMessage: String) {
    NO_ELIGIBLE_POST("순천향대학교 메뉴 게시물을 찾지 못했어요."),
    NO_MENU_IMAGES("게시물에 메뉴 이미지가 없어요."),
    NO_RECOGNIZED_TEXT("메뉴 이미지에서 글자를 읽지 못했어요."),

    /** Anything else (network, HTTP); not thrown, only used to label a failed load. */
    LOAD_FAILED("메뉴를 불러오지 못했어요."),
}

class MenuException(val error: MenuError) : Exception(error.displayMessage)
