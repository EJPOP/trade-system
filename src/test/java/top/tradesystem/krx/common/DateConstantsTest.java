package top.tradesystem.krx.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class DateConstantsTest {

    @ParameterizedTest
    @ValueSource(strings = {"20260101", "20251231", "20240229"})  // 2024 윤년
    void validDates(String basDd) {
        assertDoesNotThrow(() -> DateConstants.validateBasDd(basDd));
    }

    @ParameterizedTest
    @ValueSource(strings = {"20240230", "20250229", "20231301", "20230132", "20230000"})
    void invalidCalendarDates(String basDd) {
        assertThrows(IllegalArgumentException.class, () -> DateConstants.validateBasDd(basDd));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "2024010", "202401011", "abcdefgh"})
    void invalidFormat(String basDd) {
        assertThrows(IllegalArgumentException.class, () -> DateConstants.validateBasDd(basDd));
    }

    @Test
    void nullDate() {
        assertThrows(IllegalArgumentException.class, () -> DateConstants.validateBasDd(null));
    }

    @Test
    void strictParserRejectsLenientDate() {
        // ofPattern("yyyyMMdd") 기본은 20240230→0229 보정. STRICT는 거부.
        assertThrows(Exception.class, () ->
                LocalDate.parse("20240230", DateConstants.BAS_DD_FMT));
    }
}
