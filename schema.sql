-- =============================================================================
-- Gor3a Cloud Database Schema (Supabase / PostgreSQL)
-- Handwritten Egyptian Medical Prescription AI & Patient Health Portal
-- =============================================================================

-- 1. Users Table (Mobile App Registered Patients & Doctors)
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(64) PRIMARY KEY,
    phone VARCHAR(20) UNIQUE NOT NULL,
    name VARCHAR(150) NOT NULL,
    governorate VARCHAR(50) NOT NULL DEFAULT 'القاهرة',
    age INT,
    gender VARCHAR(10),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    last_login_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_users_phone ON users(phone);
CREATE INDEX IF NOT EXISTS idx_users_governorate ON users(governorate);

-- 2. User Prescriptions (Cloud Saved Medical History)
CREATE TABLE IF NOT EXISTS user_prescriptions (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL DEFAULT 'روشتة طبية',
    date VARCHAR(50),
    diagnosis TEXT,
    patient_age VARCHAR(50),
    patient_weight VARCHAR(50),
    medications_count INT DEFAULT 0,
    medications JSONB DEFAULT '[]'::jsonb,
    image_url TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_prescriptions_user_id ON user_prescriptions(user_id);

-- 3. Promotional Banners (Slider Advertisements with Governorate Targeting)
CREATE TABLE IF NOT EXISTS banners (
    id VARCHAR(64) PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    subtitle TEXT,
    image_url TEXT NOT NULL,
    target_type VARCHAR(20) DEFAULT 'all', -- 'all' or 'specific'
    target_governorates JSONB DEFAULT '[]'::jsonb,
    action_type VARCHAR(30) DEFAULT 'none', -- 'whatsapp', 'url', 'phone', 'drug', 'none'
    action_target TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    start_date DATE DEFAULT CURRENT_DATE,
    end_date DATE,
    display_order INT DEFAULT 1,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_banners_active ON banners(is_active);
CREATE INDEX IF NOT EXISTS idx_banners_order ON banners(display_order);
