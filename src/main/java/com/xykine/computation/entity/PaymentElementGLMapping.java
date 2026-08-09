package com.xykine.computation.entity;

import com.xykine.computation.dto.Nature;
import com.xykine.computation.dto.PayElement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "payment_element_gl_mappings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentElementGLMapping {

    @Id
    private String id;

    private String payElement;

    private Nature nature;

    private String glCodeDebit;

    private String glCodeCredit;

    private boolean taxable;

    private boolean pensionable;

    private boolean nstif;

    private boolean proration;

    private boolean calculated;
}
