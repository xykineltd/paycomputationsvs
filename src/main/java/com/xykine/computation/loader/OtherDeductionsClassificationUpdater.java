package com.xykine.computation.loader;

import com.xykine.computation.dto.Nature;
import com.xykine.computation.entity.PaymentElementGLMapping;
import com.xykine.computation.repo.PaymentElementGLMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Existing GL mappings were seeded as GROSS_DEDUCTIONS / taxable.
 * OTHER DEDUCTIONS is a net deduction and must not reduce Gross Income.
 */
@Component
public class OtherDeductionsClassificationUpdater {

    private static final Logger log = LoggerFactory.getLogger(OtherDeductionsClassificationUpdater.class);

    private final PaymentElementGLMappingRepository repository;

    public OtherDeductionsClassificationUpdater(PaymentElementGLMappingRepository repository) {
        this.repository = repository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reclassifyOtherDeductions() {
        int updated = 0;
        for (PaymentElementGLMapping mapping : repository.findAll()) {
            if (!isOtherDeductionsName(mapping.getPayElement())) {
                continue;
            }
            boolean changed = false;
            if (mapping.getNature() != Nature.NET_DEDUCTIONS) {
                mapping.setNature(Nature.NET_DEDUCTIONS);
                changed = true;
            }
            if (mapping.isTaxable()) {
                mapping.setTaxable(false);
                changed = true;
            }
            if (changed) {
                repository.save(mapping);
                updated++;
            }
        }
        if (updated > 0) {
            log.info("Reclassified {} OTHER DEDUCTIONS GL mapping(s) to NET_DEDUCTIONS, taxable=false", updated);
        }
    }

    private static boolean isOtherDeductionsName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String normalized = name.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        return "OTHER DEDUCTIONS".equals(normalized) || "OTHER DEDUCTION".equals(normalized);
    }
}
