CREATE TABLE IF NOT EXISTS lesson55_request_store (
    namespace_path VARCHAR(512) NOT NULL,
    item_key       VARCHAR(255) NOT NULL,
    value_json     LONGTEXT     NOT NULL,
    version        BIGINT       NOT NULL,
    updated_at     BIGINT       NOT NULL,
    PRIMARY KEY (namespace_path, item_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
