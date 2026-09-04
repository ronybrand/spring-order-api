package br.com.ronybrand.orderapi.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ItemTest {

    @Test
    void getSubtotal_ShouldMultiplyUnitPriceByQuantity() {
        final Item item = Item.builder().unitPrice(new BigDecimal("10.00")).quantity(3).build();

        assertThat(item.getSubtotal()).isEqualByComparingTo("30.00");
    }
}
