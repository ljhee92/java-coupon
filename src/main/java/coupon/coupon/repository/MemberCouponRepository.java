package coupon.coupon.repository;

import coupon.coupon.domain.MemberCoupon;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public interface MemberCouponRepository extends JpaRepository<MemberCoupon, Long> {
    List<MemberCoupon> findByMemberIdAndUsedAndUseEndedAtAfter(Long memberId, boolean used, LocalDateTime now);

    Long countByCoupon_Id(Long couponId);

    Long countByCoupon_IdAndUsed(Long couponId, boolean used);


    @Modifying
    @Query("update MemberCoupon mc set mc.used = :used, mc.usedAt = :usedAt where mc.id in :memberCouponIds")
    void updateUsedAndUsedAt(@Param("memberCouponIds") List<Long> memberCouponIds, @Param("used") boolean used,
                             @Param("usedAt") LocalDateTime usedAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select mc from MemberCoupon mc join fetch mc.coupon where mc.id = :id")
    Optional<MemberCoupon> findByIdWithLock(@Param("id") Long id);
}
