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
    gender VARCHAR(10) DEFAULT 'male',
    password_hash TEXT,
    password_salt TEXT,
    prescriptions_count INT DEFAULT 0,
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
    start_date VARCHAR(50),
    end_date VARCHAR(50),
    display_order INT DEFAULT 1,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_banners_active ON banners(is_active);
CREATE INDEX IF NOT EXISTS idx_banners_order ON banners(display_order);

-- 4. Supabase Row Level Security (RLS) Settings
-- Allow backend serverless functions to read and write without policy blocks
ALTER TABLE users DISABLE ROW LEVEL SECURITY;
ALTER TABLE user_prescriptions DISABLE ROW LEVEL SECURITY;
ALTER TABLE banners DISABLE ROW LEVEL SECURITY;

-- 5. Seed Initial Data (Official Promotional Banners)
INSERT INTO banners (id, title, subtitle, image_url, target_type, target_governorates, action_type, action_target, is_active, start_date, end_date, display_order)
VALUES 
(
    'banner-seif-24h',
    'توصيل روشتات مجاني 24 ساعة',
    'خدمة التوصيل السريع من صيدليات سيف تغطي القاهرة والجيزة والإسكندرية',
    'https://images.unsplash.com/photo-1576602976047-174e57a47881?auto=format&fit=crop&w=800&q=80',
    'specific',
    '["القاهرة", "الجيزة", "الإسكندرية"]'::jsonb,
    'phone',
    '19199',
    false,
    '2026-10-01',
    '2026-12-31',
    1
),
(
    'banner-gor3a-alternatives',
    'دوائك ناقص في الصيدليات؟',
    'اكتشف جميع البدائل والمثائل المصرية المعتمدة وبنفس المادة الفعالة وسعرها الرسمي',
    'https://images.unsplash.com/photo-1471864190281-a93a3070b6de?auto=format&fit=crop&w=800&q=80',
    'all',
    '[]'::jsonb,
    'url',
    '/drugs',
    true,
    '2026-10-01',
    NULL,
    2
)
ON CONFLICT (id) DO NOTHING;
