package br.com.ronybrand.orderapi.order;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ItemQuantityLimitTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void itemRequest_ShouldAcceptQuantityAtTheLimit() {
        final ItemRequestDto item = new ItemRequestDto("Widget", new BigDecimal("1.00"), ItemRequestDto.MAX_QUANTITY);

        assertThat(validator.validate(item)).isEmpty();
    }

    @Test
    void itemRequest_ShouldRejectQuantityAboveTheLimit() {
        final ItemRequestDto item = new ItemRequestDto("Widget", new BigDecimal("1.00"), ItemRequestDto.MAX_QUANTITY + 1);

        assertThat(validator.validate(item)).hasSize(1);
    }

    @Test
    void quantityUpdate_ShouldRejectQuantityAboveTheLimit() {
        final ItemQuantityUpdateRequestDto update = new ItemQuantityUpdateRequestDto(ItemRequestDto.MAX_QUANTITY + 1);

        assertThat(validator.validate(update)).hasSize(1);
    }

    @Test
    void quantityUpdate_ShouldAcceptQuantityAtTheLimit() {
        final ItemQuantityUpdateRequestDto update = new ItemQuantityUpdateRequestDto(ItemRequestDto.MAX_QUANTITY);

        assertThat(validator.validate(update)).isEmpty();
    }
}
