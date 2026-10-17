CREATE INDEX IF NOT EXISTS idx_people_dni ON people (dni);
CREATE INDEX IF NOT EXISTS idx_products_name ON products (name);
CREATE INDEX IF NOT EXISTS idx_audit_user_id ON audit (user_id);
CREATE INDEX IF NOT EXISTS idx_products_code ON products (code);