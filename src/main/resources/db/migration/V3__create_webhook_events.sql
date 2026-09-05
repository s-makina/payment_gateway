CREATE TABLE webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(50) NOT NULL,
    transaction_reference VARCHAR(50) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    signature VARCHAR(255),
    verification_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    processing_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX idx_webhook_events_reference_type ON webhook_events(transaction_reference, event_type);
CREATE INDEX idx_webhook_events_transaction_reference ON webhook_events(transaction_reference);
CREATE INDEX idx_webhook_events_processing_status ON webhook_events(processing_status);
