--liquibase formatted sql

--changeset ronybrand:2
--comment: create the orders and item tables (DOMAIN.md sections 2-3); "orders" not "order" since ORDER is a reserved SQL keyword
CREATE TABLE orders (
    id CHAR(36) NOT NULL PRIMARY KEY,
    customer_id CHAR(36) NOT NULL,
    total DECIMAL(19,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    deleted_at TIMESTAMP,
    deleted_by VARCHAR(255),
    CONSTRAINT fk_order_customer FOREIGN KEY (customer_id) REFERENCES customer (id)
);

CREATE INDEX idx_order_customer_id ON orders (customer_id);
CREATE INDEX idx_order_deleted_at ON orders (deleted_at);

CREATE TABLE item (
    id CHAR(36) NOT NULL PRIMARY KEY,
    order_id CHAR(36) NOT NULL,
    description VARCHAR(255) NOT NULL,
    unit_price DECIMAL(19,2) NOT NULL,
    quantity INTEGER NOT NULL,
    CONSTRAINT fk_item_order FOREIGN KEY (order_id) REFERENCES orders (id)
);

CREATE INDEX idx_item_order_id ON item (order_id);
--rollback DROP TABLE item;
--rollback DROP TABLE orders;
