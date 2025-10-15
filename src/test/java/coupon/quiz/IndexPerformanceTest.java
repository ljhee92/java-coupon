package coupon.quiz;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.restassured.RestAssured;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class IndexPerformanceTest {

    private static final String BASE_URI = "http://localhost:8080";
    private static final Long MIN_COUPON_ID = 1L;
    private static final Long MAX_COUPON_ID = 351160L;
    private static final Long MIN_MEMBER_ID = 1L;
    private static final Long MAX_MEMBER_ID = 250000L;
    private static final int THREAD_COUNT = 10;
    private static final int TEST_DURATION_SECONDS = 10;
    private static final long MILLISECONDS_IN_SECOND = 1000L;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = BASE_URI;
    }

    /*
        1. 해결 방안
        - member_coupon 테이블에 coupon_id를 fk로 설정한다.
        - member_coupon 테이블에 coupon_id를 단일 index로 설정한다.
        - member_coupon 테이블에 설정되어 있는 used, coupon_id 복합 인덱스의 순서를 coupon_id, used로 변경한다.
        2. 개선된 성능 시간: 127ms -> 6ms
    */
    @Test
    void 쿠폰의_발급_수량_조회() throws InterruptedException {
        AtomicBoolean running = new AtomicBoolean(false);
        AtomicInteger requestCount = new AtomicInteger(0);
        AtomicLong totalElapsedTime = new AtomicLong(0);

        int statusCode = RestAssured.get("/coupons/" + ThreadLocalRandom.current()
                        .nextLong(MIN_COUPON_ID, MAX_COUPON_ID + 1) + "/issued-count").statusCode();
        assertThat(statusCode).withFailMessage("쿠폰의 발급 수량 조회 API 호출에 실패했습니다. 테스트 대상 서버가 실행중인지 확인해 주세요.").isEqualTo(200);

        executeMultipleRequests(running, requestCount, totalElapsedTime,
                () -> RestAssured.get("/coupons/" + ThreadLocalRandom.current()
                        .nextLong(MIN_COUPON_ID, MAX_COUPON_ID + 1) + "/issued-count"));

        System.out.println("Total request count: " + requestCount.get());
        System.out.println("Total elapsed time: " + totalElapsedTime.get() + "ms");

        long averageElapsedTime = totalElapsedTime.get() / requestCount.get();
        System.out.println("Average elapsed time: " + averageElapsedTime + "ms");

        assertThat(averageElapsedTime).isLessThanOrEqualTo(100L);
    }

    /*
        해결 방안
        - 이미 member_coupon 테이블에 coupon_id, used 복합 인덱스가 걸려 있다.
    */
    @Test
    void 쿠폰의_사용_수량_조회() throws InterruptedException {
        AtomicBoolean running = new AtomicBoolean(false);
        AtomicInteger requestCount = new AtomicInteger(0);
        AtomicLong totalElapsedTime = new AtomicLong(0);

        int statusCode = RestAssured.get("/coupons/" + ThreadLocalRandom.current()
                        .nextLong(MIN_COUPON_ID, MAX_COUPON_ID + 1) + "/used-count").statusCode();
        assertThat(statusCode).withFailMessage("쿠폰의 사용 수량 조회 API 호출에 실패했습니다. 테스트 대상 서버가 실행중인지 확인해 주세요.").isEqualTo(200);

        executeMultipleRequests(running, requestCount, totalElapsedTime,
                () -> RestAssured.get("/coupons/" + ThreadLocalRandom.current()
                        .nextLong(MIN_COUPON_ID, MAX_COUPON_ID + 1) + "/used-count"));

        System.out.println("Total request count: " + requestCount.get());
        System.out.println("Total elapsed time: " + totalElapsedTime.get() + "ms");

        long averageElapsedTime = totalElapsedTime.get() / requestCount.get();
        System.out.println("Average elapsed time: " + totalElapsedTime.get() / requestCount.get() + "ms");

        assertThat(averageElapsedTime).isLessThanOrEqualTo(100L);
    }

    /*
        1. 해결 방안
        - 생성되어 있던 coupon_status 단일 인덱스 제거
        - coupon_status, issue_started_at, issue_ended_at 복합 인덱스 생성
        - 옵티마이저는 explain으로 실행 계획을 확인했을 때, rows * filtered 값으로 예상 비용(cost)를 계산한다.
          coupon_status 단일 인덱스일 경우의 예상 비용(rows=2408, filtered=1.11)이
          coupon_status, issue_started_at, issue_ended_at 복합 인덱스의 예상 비용(rows=2408, filtered=3.33)보다 낮으므로
          두 인덱스가 같이 있을 땐 단일 인덱스를 선택한다. 따라서 단일 인덱스 제거 후 복합 인덱스를 설정한다.
        2. 개선된 성능 시간: 15ms -> 4ms
    */
    @Test
    void 현재_발급_가능한_쿠폰_조회() throws InterruptedException {
        AtomicBoolean running = new AtomicBoolean(false);
        AtomicInteger requestCount = new AtomicInteger(0);
        AtomicLong totalElapsedTime = new AtomicLong(0);

        int statusCode = RestAssured.get("/coupons/issuable").statusCode();
        assertThat(statusCode).withFailMessage("발급 가능한 쿠폰 조회 API 호출에 실패했습니다. 테스트 대상 서버가 실행중인지 확인해 주세요.").isEqualTo(200);

        executeMultipleRequests(running, requestCount, totalElapsedTime, () -> RestAssured.get("/coupons/issuable"));

        System.out.println("Total request count: " + requestCount.get());
        System.out.println("Total elapsed time: " + totalElapsedTime.get() + "ms");

        long averageElapsedTime = totalElapsedTime.get() / requestCount.get();
        System.out.println("Average elapsed time: " + totalElapsedTime.get() / requestCount.get() + "ms");

        assertThat(averageElapsedTime).isLessThanOrEqualTo(500L);
    }

    /*
        1. 해결 방안
            - member_id 단일 인덱스 추가
            - 기존 쿼리는 used, coupon_id 복합 인덱스를 사용했는데, used의 카디널리티가 높기 때문에 인덱스를 타지만 의미가 없음
            - where 조건절 중 카디널리티가 높을 것으로 예상되는 member_id 를 단일 인덱스로 추가
        2. 개선된 성능 시간: 966ms -> 5ms
    */
    @Test
    void 회원이_가지고_있는_사용_가능한_쿠폰_조회() throws InterruptedException {
        AtomicBoolean running = new AtomicBoolean(false);
        AtomicInteger requestCount = new AtomicInteger(0);
        AtomicLong totalElapsedTime = new AtomicLong(0);

        int statusCode = RestAssured.get("/member-coupons/by-member-id?memberId=" + ThreadLocalRandom.current()
                .nextLong(MIN_MEMBER_ID, MAX_MEMBER_ID + 1)).statusCode();
        assertThat(statusCode).withFailMessage("회원이 가지고 있는 쿠폰 조회 API 호출에 실패했습니다. 테스트 대상 서버가 실행중인지 확인해 주세요.").isEqualTo(200);

        executeMultipleRequests(running, requestCount, totalElapsedTime,
                () -> RestAssured.get("/member-coupons/by-member-id?memberId=" + ThreadLocalRandom.current()
                        .nextLong(MIN_MEMBER_ID, MAX_MEMBER_ID + 1)));

        System.out.println("Total request count: " + requestCount.get());
        System.out.println("Total elapsed time: " + totalElapsedTime.get() + "ms");

        long averageElapsedTime = totalElapsedTime.get() / requestCount.get();
        System.out.println("Average elapsed time: " + totalElapsedTime.get() / requestCount.get() + "ms");

        assertThat(averageElapsedTime).isLessThanOrEqualTo(100L);
    }

    /*
        1. 해결 방안
            - coupon_discount_amount DESC, month, year 복합 인덱스 추가
            - coupon_discount_amount DESC 단일 인덱스 추가
            - 복합 인덱스 추가 시 단일 인덱스보다 카디널리티(중복도)가 더 줄어들지만, 크게 성능 차이는 없다.
        2. 개선된 성능 시간: 306ms
           -> 18ms(idx_year_month_coupon_discount_amount)
           -> 6~7ms(idx_coupon_discount_amount_month_year)
           -> 7ms(idx_coupon_discount_amount)
    */
    @Test
    void 월별_쿠폰_할인을_가장_많이_받은_회원_조회() throws InterruptedException {
        AtomicBoolean running = new AtomicBoolean(false);
        AtomicInteger requestCount = new AtomicInteger(0);
        AtomicLong totalElapsedTime = new AtomicLong(0);

        int statusCode = RestAssured.get("/marketing/max-coupon-discount-member?year=2019&month=1").statusCode();
        assertThat(statusCode).withFailMessage("월별 쿠폰 할인을 가장 많이 받은 회원 조회 API 호출에 실패했습니다. 테스트 대상 서버가 실행중인지 확인해 주세요.").isEqualTo(200);

        executeMultipleRequests(running, requestCount, totalElapsedTime, () -> {
            RestAssured.get(
                    "/marketing/max-coupon-discount-member?year=2019&month=" + ThreadLocalRandom.current().nextInt(1, 6));
        });

        System.out.println("Total request count: " + requestCount.get());
        System.out.println("Total elapsed time: " + totalElapsedTime.get() + "ms");

        long averageElapsedTime = totalElapsedTime.get() / requestCount.get();
        System.out.println("Average elapsed time: " + totalElapsedTime.get() / requestCount.get() + "ms");

        assertThat(averageElapsedTime).isLessThanOrEqualTo(100L);
    }

    private void executeMultipleRequests(AtomicBoolean running, AtomicInteger requestCount, AtomicLong totalElapsedTime,
                                         Runnable runnable)
            throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        for (int i = 0; i < THREAD_COUNT; i++) {
            executorService.execute(() -> executeRequest(running, requestCount, totalElapsedTime, runnable));
        }

        Thread.sleep(MILLISECONDS_IN_SECOND);    // 스레드에 실행 요청 후 1초간 대기한 후 요청을 시작하도록 변경한다.
        running.set(true);
        Thread.sleep(TEST_DURATION_SECONDS * MILLISECONDS_IN_SECOND);
        running.set(false);

        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);
    }

    private void executeRequest(AtomicBoolean running, AtomicInteger requestCount, AtomicLong totalElapsedTime,
                                Runnable runnable) {
        while (!running.get()) {
            // 요청을 시작할 때까지 대기한다.
        }

        long elapsedTime = 0;
        while (running.get()) {
            long startTime = System.currentTimeMillis();
            runnable.run();
            long endTime = System.currentTimeMillis();

            elapsedTime += endTime - startTime;
            requestCount.incrementAndGet();
        }

        totalElapsedTime.addAndGet(elapsedTime);
    }

}
