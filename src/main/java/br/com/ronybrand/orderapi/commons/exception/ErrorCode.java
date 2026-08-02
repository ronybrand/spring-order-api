package br.com.ronybrand.orderapi.commons.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Stable catalog of domain error codes (DOMAIN.md §6). The Java constant name is descriptive;
 * {@link #code} is the stable identifier exposed to the client - never reuse an existing code for
 * a different meaning, add a new entry instead.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    VALIDATION_MISSING_FIELD("VALIDATION-01", "Campo obrigatório ausente"),
    VALIDATION_CUSTOMER_TAXID_EXISTS("VALIDATION-02", "Tax ID já cadastrado para outro customer"),
    VALIDATION_CUSTOMER_PASSPORT_EXISTS("VALIDATION-03", "Passport number já cadastrado para outro customer"),
    VALIDATION_CUSTOMER_HAS_ORDERS("VALIDATION-04", "Customer possui orders associados e não pode ser excluído"),
    VALIDATION_INVALID_CUSTOMER_ID("VALIDATION-05", "customerId inexistente"),
    VALIDATION_ORDER_NOT_EDITABLE("VALIDATION-06", "Order não está em status editável"),
    VALIDATION_ORDER_EMPTY("VALIDATION-07", "Order não possui itens"),
    VALIDATION_ORDER_INVALID_STATUS_TRANSITION("VALIDATION-08", "Transição de status inválida"),
    VALIDATION_INVALID_FILTER_VALUE("VALIDATION-09", "Valor de filtro inválido"),
    VALIDATION_INVALID_SORT_FIELD("VALIDATION-10", "Campo de ordenação inválido"),
    VALIDATION_CONSTRAINT_VIOLATION("VALIDATION-11", "Violação de validação de campo"),

    RESOURCE_NOT_FOUND_CUSTOMER("RESOURCE-NOT-FOUND-01", "Customer não encontrado"),
    RESOURCE_NOT_FOUND_ORDER("RESOURCE-NOT-FOUND-02", "Order não encontrado"),
    RESOURCE_NOT_FOUND_ITEM("RESOURCE-NOT-FOUND-03", "Item não encontrado"),

    CONFLICT_CONCURRENT_MODIFICATION("CONFLICT-01", "Conflito de concorrência (modificação simultânea)"),
    CONFLICT_DATA_INTEGRITY_VIOLATION("CONFLICT-02", "Violação de integridade de dados"),

    AUTHORIZATION_ACCESS_DENIED("AUTHORIZATION-01", "Acesso negado"),
    INTERNAL_ERROR("INTERNAL-ERROR", "Erro interno inesperado");

    private final String code;
    private final String description;
}
