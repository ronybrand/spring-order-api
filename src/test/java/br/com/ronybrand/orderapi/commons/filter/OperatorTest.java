package br.com.ronybrand.orderapi.commons.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ronybrand.orderapi.commons.exception.InvalidInputException;
import org.junit.jupiter.api.Test;

class OperatorTest {

    @Test
    void fromValue_ShouldResolveOperator_CaseInsensitively() {
        assertThat(Operator.fromValue("eq")).isEqualTo(Operator.EQ);
        assertThat(Operator.fromValue("BETWEEN")).isEqualTo(Operator.BETWEEN);
    }

    @Test
    void fromValue_ShouldThrowInvalidInputException_WhenOperatorIsUnknown() {
        assertThatThrownBy(() -> Operator.fromValue("unknown")).isInstanceOf(InvalidInputException.class);
    }
}
