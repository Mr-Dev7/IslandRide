package com.islandride.administration.service;

import com.islandride.booking.domain.OverbookingExceptionStatus;
import com.islandride.booking.domain.SecurityDepositRecord;
import com.islandride.booking.domain.SecurityDepositStatus;
import com.islandride.booking.repository.BookingRepository;
import com.islandride.booking.repository.OverbookingExceptionRecordRepository;
import com.islandride.booking.repository.SecurityDepositRecordRepository;
import com.islandride.booking.service.BookingDtoMapper;
import com.islandride.commission.domain.Commission;
import com.islandride.commission.repository.CommissionRepository;
import com.islandride.paymentfinance.domain.OfflineCollection;
import com.islandride.paymentfinance.domain.Payment;
import com.islandride.paymentfinance.domain.PaymentMethod;
import com.islandride.paymentfinance.domain.PaymentStatus;
import com.islandride.paymentfinance.domain.Refund;
import com.islandride.paymentfinance.domain.RefundStatus;
import com.islandride.paymentfinance.dto.PaymentDtos;
import com.islandride.paymentfinance.repository.OfflineCollectionRepository;
import com.islandride.paymentfinance.repository.PaymentRepository;
import com.islandride.paymentfinance.repository.PaymentWebhookEventRepository;
import com.islandride.paymentfinance.repository.RefundRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class AdminFinanceOverviewService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final OfflineCollectionRepository offlineCollectionRepository;
    private final SecurityDepositRecordRepository depositRepository;
    private final OverbookingExceptionRecordRepository overbookingRepository;
    private final PaymentWebhookEventRepository webhookRepository;
    private final CommissionRepository commissionRepository;

    public AdminFinanceOverviewService(PaymentRepository paymentRepository,
                                       RefundRepository refundRepository,
                                       OfflineCollectionRepository offlineCollectionRepository,
                                       SecurityDepositRecordRepository depositRepository,
                                       OverbookingExceptionRecordRepository overbookingRepository,
                                       PaymentWebhookEventRepository webhookRepository,
                                       CommissionRepository commissionRepository) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.offlineCollectionRepository = offlineCollectionRepository;
        this.depositRepository = depositRepository;
        this.overbookingRepository = overbookingRepository;
        this.webhookRepository = webhookRepository;
        this.commissionRepository = commissionRepository;
    }

    @Transactional(readOnly = true)
    public PaymentDtos.AdminFinanceOverviewDto getFinanceOverview() {
        BigDecimal totalOnlineCaptured = paymentRepository.findAll().stream()
                .filter(p -> p.getPaymentMethod() == PaymentMethod.GATEWAY)
                .filter(p -> p.getStatus() == PaymentStatus.CAPTURED || p.getStatus() == PaymentStatus.REFUNDED)
                .filter(p -> !p.isDeposit())
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalOfflineRecorded = offlineCollectionRepository.findAll().stream()
                .filter(o -> "PICKUP_BALANCE".equalsIgnoreCase(o.getCollectionType()))
                .map(OfflineCollection::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalPlatformCommission = commissionRepository.findAll().stream()
                .map(Commission::getCommissionAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalPendingRefunds = refundRepository.findAll().stream()
                .filter(r -> r.getStatus() == RefundStatus.PENDING || r.getStatus() == RefundStatus.PROCESSING)
                .map(Refund::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalCompletedRefunds = refundRepository.findAll().stream()
                .filter(r -> r.getStatus() == RefundStatus.SUCCESS || r.getStatus() == RefundStatus.RECONCILED)
                .map(Refund::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        long failedRefundsCount = refundRepository.findAll().stream()
                .filter(r -> r.getStatus() == RefundStatus.FAILED)
                .count();

        long openOverbookingCount = overbookingRepository.findByStatusOrderByDetectedAtDesc(OverbookingExceptionStatus.DETECTED).size();

        PaymentDtos.SecurityDepositTrustSummaryDto depositSummary = computeDepositTrustSummary();

        var recentPayments = paymentRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 10))
                .map(BookingDtoMapper::toPaymentDto)
                .getContent();

        var recentRefunds = refundRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 10))
                .map(BookingDtoMapper::toRefundDto)
                .getContent();

        var recentWebhooks = webhookRepository.findAllByOrderByReceivedAtDesc(PageRequest.of(0, 10))
                .map(BookingDtoMapper::toWebhookDto)
                .getContent();

        var openOverbookings = overbookingRepository.findByStatusOrderByDetectedAtDesc(OverbookingExceptionStatus.DETECTED).stream()
                .map(BookingDtoMapper::toOverbookingDto)
                .toList();

        return new PaymentDtos.AdminFinanceOverviewDto(
                totalOnlineCaptured,
                totalOfflineRecorded,
                totalPlatformCommission,
                totalPendingRefunds,
                totalCompletedRefunds,
                failedRefundsCount,
                openOverbookingCount,
                depositSummary,
                recentPayments,
                recentRefunds,
                recentWebhooks,
                openOverbookings
        );
    }

    @Transactional(readOnly = true)
    public PaymentDtos.SecurityDepositTrustSummaryDto computeDepositTrustSummary() {
        List<SecurityDepositRecord> allDeposits = depositRepository.findAll();

        BigDecimal expected = allDeposits.stream()
                .map(SecurityDepositRecord::getExpectedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal collectedHeld = allDeposits.stream()
                .filter(d -> d.getStatus() == SecurityDepositStatus.COLLECTED)
                .map(SecurityDepositRecord::getCollectedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal inspectionHeld = allDeposits.stream()
                .filter(d -> d.getStatus() == SecurityDepositStatus.INSPECTION_HOLD)
                .map(SecurityDepositRecord::getCollectedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal released = allDeposits.stream()
                .map(SecurityDepositRecord::getRefundedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal forfeited = allDeposits.stream()
                .map(SecurityDepositRecord::getForfeitedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        long activeHeldCount = allDeposits.stream()
                .filter(d -> d.getStatus() == SecurityDepositStatus.COLLECTED)
                .count();

        long inspectionHoldCount = allDeposits.stream()
                .filter(d -> d.getStatus() == SecurityDepositStatus.INSPECTION_HOLD)
                .count();

        return new PaymentDtos.SecurityDepositTrustSummaryDto(
                expected,
                collectedHeld,
                inspectionHeld,
                released,
                forfeited,
                activeHeldCount,
                inspectionHoldCount
        );
    }
}
