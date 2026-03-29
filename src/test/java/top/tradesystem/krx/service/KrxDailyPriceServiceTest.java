package top.tradesystem.krx.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 403 누적 판정 로직 단위 테스트.
 * isSkippable은 private이므로 리플렉션으로 접근.
 */
class KrxDailyPriceServiceTest {

    @Test
    void consecutive403_countsUp() throws Exception {
        var service = createServiceInstance();
        var method = getIsSkippable(service);
        var counter = new AtomicInteger(0);

        var ex403 = WebClientResponseException.create(403, "Forbidden", null, null, null);

        // 1~4회: skippable
        for (int i = 0; i < 4; i++) {
            boolean result = (boolean) method.invoke(service, ex403, "403 Forbidden", counter);
            assertTrue(result, "403 #" + (i + 1) + " should be skippable");
        }

        // 5회: 인증키 만료 — not skippable
        boolean fifth = (boolean) method.invoke(service, ex403, "403 Forbidden", counter);
        assertFalse(fifth, "5th consecutive 403 should NOT be skippable");
    }

    @Test
    void non403_resetsCounter() throws Exception {
        var service = createServiceInstance();
        var method = getIsSkippable(service);
        var counter = new AtomicInteger(3);  // 이미 3회

        var ex500 = WebClientResponseException.create(500, "Server Error", null, null, null);
        boolean result = (boolean) method.invoke(service, ex500, "500 error", counter);
        assertFalse(result, "500 should not be skippable");
        assertEquals(0, counter.get(), "counter should reset on non-403");
    }

    @Test
    void separateCounters_dontInterfere() throws Exception {
        var service = createServiceInstance();
        var method = getIsSkippable(service);
        var counterA = new AtomicInteger(0);
        var counterB = new AtomicInteger(0);

        var ex403 = WebClientResponseException.create(403, "Forbidden", null, null, null);

        // 요청 A: 3번 403
        for (int i = 0; i < 3; i++) {
            method.invoke(service, ex403, "403", counterA);
        }

        // 요청 B: 첫 403
        boolean result = (boolean) method.invoke(service, ex403, "403", counterB);
        assertTrue(result, "Request B's first 403 should be skippable (counter=1)");
        assertEquals(1, counterB.get());
        assertEquals(3, counterA.get());
    }

    // ── 헬퍼 ──

    private KrxDailyPriceService createServiceInstance() throws Exception {
        // 생성자 파라미터 없이 인스턴스 생성 (리플렉션)
        var ctor = KrxDailyPriceService.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        // 생성자가 파라미터를 요구하면 null로 채움
        Object[] args = new Object[ctor.getParameterCount()];
        return (KrxDailyPriceService) ctor.newInstance(args);
    }

    private Method getIsSkippable(Object service) throws Exception {
        Method m = KrxDailyPriceService.class.getDeclaredMethod(
                "isSkippable", Throwable.class, String.class, AtomicInteger.class);
        m.setAccessible(true);
        return m;
    }
}
