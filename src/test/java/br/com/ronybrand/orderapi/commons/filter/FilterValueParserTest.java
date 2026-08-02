package br.com.ronybrand.orderapi.commons.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ronybrand.orderapi.commons.exception.InvalidInputException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FilterValueParserTest {

    private enum Status { OPEN, CLOSED }

    @Test
    void parse_ShouldConvertToTargetType() {
        assertThat(FilterValueParser.parse("42", Integer.class)).isEqualTo(42);
        assertThat(FilterValueParser.parse("19.90", BigDecimal.class)).isEqualByComparingTo("19.90");
        final UUID id = UUID.randomUUID();
        assertThat(FilterValueParser.parse(id.toString(), UUID.class)).isEqualTo(id);
    }

    @Test
    void parse_ShouldMatchEnumConstant_CaseInsensitively() {
        assertThat(FilterValueParser.parse("open", Status.class)).isEqualTo(Status.OPEN);
    }

    @Test
    void parse_ShouldThrowInvalidInputException_WhenValueIsIncompatibleWithType() {
        assertThatThrownBy(() -> FilterValueParser.parse("not-a-number", Integer.class))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void parse_ShouldThrowInvalidInputException_WhenEnumConstantDoesNotExist() {
        assertThatThrownBy(() -> FilterValueParser.parse("UNKNOWN", Status.class))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void parseList_ShouldSplitOnCommaAndTrim() {
        final List<Integer> result = FilterValueParser.parseList("1, 2,3", Integer.class);

        assertThat(result).containsExactly(1, 2, 3);
    }
}
