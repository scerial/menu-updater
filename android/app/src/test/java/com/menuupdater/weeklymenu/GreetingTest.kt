package com.menuupdater.weeklymenu

import org.junit.Assert.assertTrue
import org.junit.Test

class GreetingTest {
    @Test
    fun greetingMentionsMenu() {
        assertTrue(greeting().contains("메뉴"))
    }
}
