package com.akira.educacion.billing.service;

import com.akira.educacion.audit.AuditService;
import com.akira.educacion.billing.api.ReceiptResponse;
import com.akira.educacion.billing.domain.Payment;
import com.akira.educacion.billing.domain.Receipt;
import com.akira.educacion.billing.repository.ReceiptRepository;
import com.akira.educacion.common.api.NotFoundException;
import com.akira.educacion.common.api.PageResponse;
import com.akira.educacion.institution.domain.Institution;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Recibos internos. La numeración es secuencial por institución y se obtiene
 * con una operación atómica en base de datos, válida incluso con registros
 * concurrentes.
 */
@Service
@RequiredArgsConstructor
public class ReceiptService {

    private final ReceiptRepository receiptRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AuditService auditService;

    @Transactional
    public Receipt issue(Institution institution, Payment payment, String issuedByEmail) {
        if (receiptRepository.existsByInstitutionIdAndPaymentId(institution.getId(), payment.getId())) {
            return receiptRepository.findByInstitutionIdAndPaymentId(institution.getId(), payment.getId())
                    .orElseThrow(() -> new IllegalStateException("El recibo existe pero no pudo recuperarse"));
        }

        Long sequence = jdbcTemplate.queryForObject("""
                INSERT INTO receipt_counter (institution_id, last_number)
                VALUES (?, 1)
                ON CONFLICT (institution_id)
                DO UPDATE SET last_number = receipt_counter.last_number + 1
                RETURNING last_number
                """, Long.class, institution.getId());
        if (sequence == null) {
            throw new IllegalStateException("No fue posible generar la numeración del recibo");
        }

        Receipt receipt = new Receipt();
        receipt.setInstitution(institution);
        receipt.setPayment(payment);
        receipt.setSequenceNumber(sequence);
        receipt.setReceiptNumber("REC-%d-%06d".formatted(LocalDate.now().getYear(), sequence));
        receipt.setAmount(payment.getAmount());
        receipt.setCurrency(payment.getCurrency());
        receipt.setIssuedAt(Instant.now());
        receipt.setIssuedByEmail(issuedByEmail);
        receipt.setVoided(false);

        Receipt saved = receiptRepository.save(receipt);
        auditService.record("RECEIPT_ISSUED", "Receipt", saved.getId(), institution.getId(),
                "paymentId=" + payment.getId() + "; number=" + saved.getReceiptNumber());
        return saved;
    }

    @Transactional
    public void voidReceipt(Receipt receipt, String reason) {
        if (receipt.isVoided()) {
            return;
        }
        receipt.setVoided(true);
        receipt.setVoidReason(reason);
        receipt.setVoidedAt(Instant.now());
        receiptRepository.save(receipt);
        auditService.record("RECEIPT_VOIDED", "Receipt", receipt.getId(), receipt.getInstitution().getId(),
                "paymentId=" + receipt.getPayment().getId() + "; reason=" + reason);
    }

    @Transactional(readOnly = true)
    public ReceiptResponse get(UUID institutionId, UUID id) {
        Receipt receipt = receiptRepository.findByIdAndInstitutionId(id, institutionId)
                .orElseThrow(() -> new NotFoundException("El recibo no existe en la institución"));
        return toResponse(receipt);
    }

    @Transactional(readOnly = true)
    public ReceiptResponse getByPayment(UUID institutionId, UUID paymentId) {
        Receipt receipt = receiptRepository.findByInstitutionIdAndPaymentId(institutionId, paymentId)
                .orElseThrow(() -> new NotFoundException("El pago no tiene un recibo emitido"));
        return toResponse(receipt);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReceiptResponse> list(UUID institutionId, UUID paymentId, UUID studentId,
            Pageable pageable) {
        Page<Receipt> page = receiptRepository.search(institutionId, paymentId != null, paymentId,
                studentId != null, studentId, pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    public ReceiptResponse toResponse(Receipt receipt) {
        Payment payment = receipt.getPayment();
        return new ReceiptResponse(
                receipt.getId(),
                receipt.getReceiptNumber(),
                receipt.getSequenceNumber(),
                payment.getId(),
                payment.getStudent().getId(),
                payment.getStudent().fullName(),
                receipt.getAmount(),
                receipt.getCurrency(),
                receipt.getIssuedAt(),
                receipt.getIssuedByEmail(),
                receipt.isVoided(),
                receipt.getVoidReason(),
                receipt.getVoidedAt(),
                payment.getMethod(),
                payment.getReference(),
                payment.getStatus(),
                payment.getReceivedDate());
    }
}
