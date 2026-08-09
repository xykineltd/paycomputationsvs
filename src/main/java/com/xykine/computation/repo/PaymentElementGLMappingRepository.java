package com.xykine.computation.repo;

import com.xykine.computation.dto.Nature;
import com.xykine.computation.dto.PayElement;
import com.xykine.computation.entity.PaymentElementGLMapping;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentElementGLMappingRepository
        extends MongoRepository<PaymentElementGLMapping, String> {

    Optional<PaymentElementGLMapping> findByPayElement(
            String payElement
    );

    List<PaymentElementGLMapping> findAll();

    List<PaymentElementGLMapping> findByPayElementIn(
            List<PayElement> payElements
    );

    List<PaymentElementGLMapping> findByNatureIn(
            List<PayElement> natures
    );

    Optional<PaymentElementGLMapping> findByPayElementAndNature(
            PayElement payElement,
            Nature nature
    );

    boolean existsByPayElement(
            PayElement payElement
    );

    boolean existsByPayElementAndNature(
            PayElement payElement,
            Nature nature
    );

    List<PaymentElementGLMapping> findByTaxableTrue();

    List<PaymentElementGLMapping> findByTaxableFalse();

    List<PaymentElementGLMapping> findByPensionableTrue();

    List<PaymentElementGLMapping> findByPensionableFalse();

    List<PaymentElementGLMapping> findByNstifTrue();

    List<PaymentElementGLMapping> findByNstifFalse();

    List<PaymentElementGLMapping> findByProrationTrue();

    List<PaymentElementGLMapping> findByProrationFalse();

    List<PaymentElementGLMapping> findByGlCodeDebit(
            String glCodeDebit
    );

    List<PaymentElementGLMapping> findByGlCodeCredit(
            String glCodeCredit
    );
}
