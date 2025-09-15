-- PostgreSQL initialization script for JSimpleRag
-- This script runs when the PostgreSQL container starts for the first time

-- Create extensions
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Set locale for full-text search (Portuguese)
-- Note: This might need adjustment based on your locale requirements
UPDATE pg_database SET datcollate = 'pt_BR.UTF-8', datctype = 'pt_BR.UTF-8' WHERE datname = 'rag_db';

-- Create a simple health check function
CREATE OR REPLACE FUNCTION health_check()
RETURNS TEXT AS $$
BEGIN
    RETURN 'JSimpleRag PostgreSQL is ready with PGVector extension';
END;
$$ LANGUAGE plpgsql;

-- Grant necessary permissions
GRANT ALL PRIVILEGES ON DATABASE rag_db TO rag_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO rag_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO rag_user;

-- Log initialization
\echo 'JSimpleRag PostgreSQL initialization completed'
\echo 'PGVector extension enabled'
\echo 'Ready for Liquibase migrations'