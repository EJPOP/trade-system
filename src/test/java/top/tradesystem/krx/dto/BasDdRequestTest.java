package top.tradesystem.krx.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class BasDdRequestTest {

    @Test
    void validDate() {
        assertDoesNotThrow(() -> new BasDdRequest("20260328"));
    }

    @Test
    void invalidCalendar_20240230() {
        assertThrows(IllegalArgumentException.class, () -> new BasDdRequest("20240230"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"20250229", "20231301", "20230132"})
    void invalidCalendarDates(String basDd) {
        assertThrows(IllegalArgumentException.class, () -> new BasDdRequest(basDd));
    }

    @Test
    void leapYear2024() {
        assertDoesNotThrow(() -> new BasDdRequest("20240229"));
    }
}
