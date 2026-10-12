"""
Realistic SVG Pharmaceutical Packaging Generator for Egyptian Medicines.
Generates crisp, responsive, high-resolution vector visuals of Egyptian medicine
boxes, blister packs, syrup bottles, and ampoules with official EDA styling.
"""

import html
import re
from typing import Optional, Tuple


def get_category_color_palette(
    category_slug: str,
    dosage_form: str = "Tablet"
) -> Tuple[str, str, str, str]:
    """
    Returns (primary_color, secondary_color, accent_color, text_on_primary)
    based on therapeutic pharmacological category and dosage form.
    """
    cat = (category_slug or "").upper()
    form = (dosage_form or "").lower()

    if "ANTIBIOTIC" in cat or "ANTI-INFECTIVE" in cat:
        return ("#1E3A8A", "#3B82F6", "#F59E0B", "#FFFFFF")  # Navy & Gold / Electric Blue
    elif "ANALGESIC" in cat or "ANTI-INFLAMMATORY" in cat or "NSAID" in cat:
        return ("#B91C1C", "#EF4444", "#F87171", "#FFFFFF")  # Crimson Red & Coral
    elif "GASTRO" in cat or "ANTACID" in cat or "ANTI-ULCER" in cat or "ANTIDIARRHEAL" in cat:
        return ("#047857", "#10B981", "#34D399", "#FFFFFF")  # Emerald Green
    elif "RESPIRATORY" in cat or "COUGH" in cat or "BRONCHODILATOR" in cat or "EXPECTORANT" in cat:
        return ("#0369A1", "#0284C7", "#38BDF8", "#FFFFFF")  # Sky Blue / Cyan
    elif "COLD" in cat or "ALLERGY" in cat or "ANTIHISTAMINE" in cat:
        return ("#6D28D9", "#8B5CF6", "#C084FC", "#FFFFFF")  # Royal Purple / Lavender
    elif "VITAMIN" in cat or "SUPPLEMENT" in cat or "MINERAL" in cat:
        return ("#D97706", "#F59E0B", "#FBBF24", "#FFFFFF")  # Amber / Gold
    elif "CARDIO" in cat or "HYPERTENSION" in cat:
        return ("#991B1B", "#DC2626", "#FCA5A5", "#FFFFFF")  # Cardiac Red
    elif "DIABET" in cat:
        return ("#0F766E", "#14B8A6", "#2DD4BF", "#FFFFFF")  # Teal
    elif "MUSCLE" in cat or "SPASM" in cat:
        return ("#C2410C", "#EA580C", "#FB923C", "#FFFFFF")  # Terracotta / Orange
    elif "DERMA" in cat or "TOPICAL" in cat:
        return ("#0E7490", "#06B6D4", "#67E8F9", "#FFFFFF")  # Cyan / Marine

    # Fallbacks by dosage form
    if "susp" in form or "syr" in form or "liquid" in form:
        return ("#0284C7", "#38BDF8", "#F59E0B", "#FFFFFF")
    elif "inj" in form or "amp" in form or "vial" in form:
        return ("#0F766E", "#14B8A6", "#EF4444", "#FFFFFF")
    elif "drop" in form:
        return ("#0D9488", "#2DD4BF", "#38BDF8", "#FFFFFF")
    elif "cream" in form or "oint" in form or "gel" in form:
        return ("#4338CA", "#6366F1", "#A5B4FC", "#FFFFFF")

    # Standard clean pharma blue
    return ("#2563EB", "#3B82F6", "#60A5FA", "#FFFFFF")


def render_dosage_form_icon(dosage_form: str, accent_color: str) -> str:
    """Renders vector artwork for the specific dosage form inside the packaging."""
    form = (dosage_form or "").lower()

    if "susp" in form or "syr" in form or "liquid" in form or "معلق" in form or "شراب" in form:
        # Medicine bottle with liquid level & dropper/spoon
        return f"""
        <g transform="translate(290, 85)">
            <!-- Glass Bottle Body -->
            <rect x="18" y="28" width="48" height="74" rx="8" fill="#B45309" opacity="0.9"/>
            <!-- Liquid Level Highlight -->
            <rect x="22" y="44" width="40" height="54" rx="4" fill="#D97706" opacity="0.8"/>
            <path d="M 22 46 Q 42 40 62 46 L 62 94 rx 4 L 22 94 Z" fill="#F59E0B" opacity="0.6"/>
            <!-- Bottle Neck & Cap -->
            <rect x="32" y="16" width="20" height="12" fill="#E2E8F0" rx="2"/>
            <rect x="28" y="8" width="28" height="10" rx="3" fill="#FFFFFF" stroke="#CBD5E1" stroke-width="1.5"/>
            <!-- Paper Label on Bottle -->
            <rect x="24" y="52" width="36" height="34" rx="3" fill="#FFFFFF" opacity="0.95"/>
            <line x1="28" y1="60" x2="56" y2="60" stroke="#0284C7" stroke-width="2"/>
            <line x1="28" y1="66" x2="52" y2="66" stroke="#94A3B8" stroke-width="1.5"/>
            <line x1="28" y1="72" x2="48" y2="72" stroke="#94A3B8" stroke-width="1.5"/>
            <!-- Measuring Spoon -->
            <path d="M 68 85 Q 82 95 90 85 Q 92 65 80 65 Z" fill="#E0F2FE" opacity="0.75" stroke="#38BDF8" stroke-width="1.5"/>
            <rect x="80" y="80" width="24" height="4" rx="2" fill="#BAE6FD"/>
        </g>
        """

    elif "inj" in form or "amp" in form or "vial" in form or "حقن" in form or "أمبول" in form:
        # Sterile Ampoule + Vial
        return f"""
        <g transform="translate(295, 80)">
            <!-- Glass Ampoule -->
            <path d="M 38 12 L 46 12 L 46 26 L 54 36 L 54 94 Q 54 98 42 98 Q 30 98 30 94 L 30 36 L 38 26 Z" 
                  fill="#E2E8F0" opacity="0.85" stroke="#94A3B8" stroke-width="1.5"/>
            <!-- Liquid Inside Ampoule -->
            <path d="M 32 50 L 52 50 L 52 92 Q 52 96 42 96 Q 32 96 32 92 Z" fill="{accent_color}" opacity="0.75"/>
            <!-- Ampoule Break Ring Band -->
            <rect x="36" y="24" width="12" height="3" fill="#EF4444" rx="1"/>
            <!-- Droplet symbol -->
            <path d="M 72 45 C 72 40, 78 32, 78 32 C 78 32, 84 40, 84 45 C 84 49, 81 52, 78 52 C 75 52, 72 49, 72 45 Z" fill="#38BDF8"/>
        </g>
        """

    elif "drop" in form or "قطرة" in form or "نقط" in form:
        # Eye / Ear Dropper Bottle
        return f"""
        <g transform="translate(295, 82)">
            <!-- Dropper Cap & Pipette Top -->
            <rect x="34" y="8" width="16" height="12" rx="4" fill="#64748B"/>
            <rect x="30" y="20" width="24" height="10" rx="2" fill="#FFFFFF" stroke="#CBD5E1" stroke-width="1.5"/>
            <!-- Bottle Body -->
            <rect x="22" y="30" width="40" height="66" rx="6" fill="#F8FAFC" stroke="#94A3B8" stroke-width="1.5"/>
            <rect x="26" y="44" width="32" height="42" rx="3" fill="{accent_color}" opacity="0.3"/>
            <!-- Dropper Tip with Droplet -->
            <path d="M 70 55 C 70 50, 76 42, 76 42 C 76 42, 82 50, 82 55 C 82 59, 79 62, 76 62 C 73 62, 70 59, 70 55 Z" fill="#0284C7"/>
        </g>
        """

    elif "cream" in form or "oint" in form or "gel" in form or "مرهم" in form or "كريم" in form or "دهان" in form:
        # Aluminum Tube
        return f"""
        <g transform="translate(285, 95) rotate(-25)">
            <!-- Tube Body -->
            <polygon points="15,20 75,20 70,85 20,85" fill="#E2E8F0" stroke="#94A3B8" stroke-width="1.5"/>
            <rect x="20" y="32" width="50" height="28" fill="{accent_color}" opacity="0.8"/>
            <!-- Tube Crimp End -->
            <rect x="18" y="82" width="54" height="6" fill="#94A3B8" rx="1"/>
            <!-- Tube Cap -->
            <rect x="35" y="6" width="20" height="14" rx="2" fill="#3B82F6"/>
        </g>
        """

    else:
        # Default: Tablets / Capsules Blister Strip
        return f"""
        <g transform="translate(285, 78)">
            <!-- Blister Pack Foil Sheet -->
            <rect x="10" y="10" width="88" height="96" rx="8" fill="#F1F5F9" stroke="#CBD5E1" stroke-width="1.5"/>
            <!-- Grid of 6 Blister Cavities / Pills -->
            <!-- Row 1 -->
            <ellipse cx="32" cy="30" rx="14" ry="10" fill="#FFFFFF" stroke="#94A3B8" stroke-width="1"/>
            <ellipse cx="32" cy="30" rx="10" ry="7" fill="{accent_color}" opacity="0.85"/>
            <ellipse cx="76" cy="30" rx="14" ry="10" fill="#FFFFFF" stroke="#94A3B8" stroke-width="1"/>
            <ellipse cx="76" cy="30" rx="10" ry="7" fill="{accent_color}" opacity="0.85"/>
            <!-- Row 2 -->
            <ellipse cx="32" cy="58" rx="14" ry="10" fill="#FFFFFF" stroke="#94A3B8" stroke-width="1"/>
            <ellipse cx="32" cy="58" rx="10" ry="7" fill="{accent_color}" opacity="0.85"/>
            <ellipse cx="76" cy="58" rx="14" ry="10" fill="#FFFFFF" stroke="#94A3B8" stroke-width="1"/>
            <ellipse cx="76" cy="58" rx="10" ry="7" fill="{accent_color}" opacity="0.85"/>
            <!-- Row 3 -->
            <ellipse cx="32" cy="86" rx="14" ry="10" fill="#FFFFFF" stroke="#94A3B8" stroke-width="1"/>
            <ellipse cx="32" cy="86" rx="10" ry="7" fill="{accent_color}" opacity="0.85"/>
            <ellipse cx="76" cy="86" rx="14" ry="10" fill="#FFFFFF" stroke="#94A3B8" stroke-width="1"/>
            <ellipse cx="76" cy="86" rx="10" ry="7" fill="{accent_color}" opacity="0.85"/>
            <!-- Foil Perforation Line -->
            <line x1="54" y1="14" x2="54" y2="102" stroke="#CBD5E1" stroke-width="1" stroke-dasharray="3,3"/>
        </g>
        """


def generate_drug_package_svg(
    commercial_name_en: str,
    commercial_name_ar: Optional[str] = None,
    strength: Optional[str] = None,
    dosage_form: str = "Tablet",
    generic_name: Optional[str] = None,
    category_slug: Optional[str] = None,
    eda_id: Optional[str] = None,
    manufacturer: Optional[str] = None,
    price_egp: Optional[float] = None,
) -> str:
    """
    Renders an authentic, vector SVG pharmaceutical package for any Egyptian medicine.
    Crisp at any display resolution with authentic Egyptian regulatory stamps.
    """
    name_en = (commercial_name_en or "Egyptian Medicine").strip()
    name_ar = (commercial_name_ar or "").strip()
    clean_strength = (strength or "").strip()
    clean_form = (dosage_form or "Tablet").strip()
    clean_gen = (generic_name or "").strip()
    clean_mfg = (manufacturer or "Egyptian Pharmaceutical Industry").strip()
    clean_eda = (eda_id or "EDA-REG").strip()

    # Determine colors
    primary, secondary, accent, text_color = get_category_color_palette(
        category_slug or clean_gen, clean_form
    )

    # Format title length to prevent SVG overflow
    font_size_en = 28
    if len(name_en) > 16:
        font_size_en = 22
    if len(name_en) > 22:
        font_size_en = 18

    # Escape texts for XML/SVG safety
    esc_name_en = html.escape(name_en)
    esc_name_ar = html.escape(name_ar)
    esc_strength = html.escape(clean_strength)
    esc_form = html.escape(clean_form)
    esc_gen = html.escape(clean_gen)
    esc_mfg = html.escape(clean_mfg)
    esc_eda = html.escape(clean_eda)

    # Dosage Form icon
    form_art = render_dosage_form_icon(clean_form, accent)

    price_tag = f"{price_egp:.1f} EGP" if price_egp else "EDA Regulated"

    svg = f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 420 260" width="100%" height="100%" style="border-radius: 12px; overflow: hidden;">
    <defs>
        <!-- Background Gradients -->
        <linearGradient id="boxGrad" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stop-color="#FFFFFF"/>
            <stop offset="60%" stop-color="#F8FAFC"/>
            <stop offset="100%" stop-color="#EDF2F7"/>
        </linearGradient>
        <linearGradient id="headerGrad" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stop-color="{primary}"/>
            <stop offset="100%" stop-color="{secondary}"/>
        </linearGradient>
        <linearGradient id="accentStripe" x1="0%" y1="0%" x2="100%" y2="0%">
            <stop offset="0%" stop-color="{accent}"/>
            <stop offset="100%" stop-color="{primary}"/>
        </linearGradient>
        <!-- Soft Realistic Box Shadow -->
        <filter id="boxShadow" x="-5%" y="-5%" width="110%" height="115%" filterUnits="userSpaceOnUse">
            <feDropShadow dx="0" dy="6" stdDeviation="8" flood-color="#0F172A" flood-opacity="0.12"/>
        </filter>
        <filter id="pillGlow" x="-10%" y="-10%" width="120%" height="120%">
            <feDropShadow dx="0" dy="2" stdDeviation="3" flood-color="{primary}" flood-opacity="0.25"/>
        </filter>
    </defs>

    <!-- Main Medicine Pack Container -->
    <rect x="8" y="8" width="404" height="244" rx="14" fill="url(#boxGrad)" stroke="#CBD5E1" stroke-width="1.5" filter="url(#boxShadow)"/>

    <!-- Top Regulatory & Brand Header Banner -->
    <path d="M 8 22 Q 8 8 22 8 L 398 8 Q 412 8 412 22 L 412 62 L 8 62 Z" fill="url(#headerGrad)"/>
    
    <!-- Accent Decorative Stripe -->
    <rect x="8" y="62" width="404" height="5" fill="url(#accentStripe)"/>

    <!-- Egyptian Drug Authority (EDA) Verification Badge (Left Top) -->
    <g transform="translate(24, 20)">
        <rect x="0" y="0" width="86" height="22" rx="4" fill="#FFFFFF" fill-opacity="0.18" stroke="#FFFFFF" stroke-width="0.8"/>
        <text x="7" y="15" font-family="'Inter', sans-serif" font-size="10" font-weight="700" fill="#FFFFFF" letter-spacing="0.5">🇪🇬 EDA REG</text>
    </g>

    <!-- Official Rx Symbol -->
    <g transform="translate(122, 19)">
        <text x="0" y="18" font-family="'Inter', 'Cairo', serif" font-size="20" font-weight="900" fill="{accent}">℞</text>
    </g>

    <!-- Arabic Brand Name on Banner (Right Top) -->
    {f'''
    <g transform="translate(396, 36)">
        <text x="0" y="0" text-anchor="end" font-family="'Cairo', 'Tahoma', sans-serif" font-size="19" font-weight="700" fill="#FFFFFF">
            {esc_name_ar}
        </text>
    </g>
    ''' if esc_name_ar else ''}

    <!-- Primary Commercial Brand Name (English) -->
    <g transform="translate(24, 102)">
        <text x="0" y="0" font-family="'Inter', 'Helvetica Neue', sans-serif" font-size="{font_size_en}" font-weight="800" fill="#0F172A" letter-spacing="-0.5">
            {esc_name_en}
        </text>
    </g>

    <!-- Strength / Concentration Badge -->
    {f'''
    <g transform="translate(24, 114)">
        <rect x="0" y="2" width="{max(60, len(esc_strength) * 9 + 18)}" height="24" rx="6" fill="{primary}" fill-opacity="0.1" stroke="{primary}" stroke-width="1.2"/>
        <text x="9" y="18" font-family="'JetBrains Mono', monospace" font-size="12" font-weight="700" fill="{primary}">
            {esc_strength}
        </text>
    </g>
    ''' if esc_strength else ''}

    <!-- Generic / Active Ingredient Line -->
    <g transform="translate(24, 158)">
        <text x="0" y="0" font-family="'Inter', sans-serif" font-size="11" font-weight="500" fill="#64748B">
            Active: <tspan font-weight="600" fill="#334155">{esc_gen[:38] + ('...' if len(esc_gen) > 38 else '') if esc_gen else 'Regulated Egyptian Formulation'}</tspan>
        </text>
    </g>

    <!-- Dosage Form & Pack Size Ribbon -->
    <g transform="translate(24, 182)">
        <rect x="0" y="0" width="110" height="20" rx="10" fill="#F1F5F9" stroke="#E2E8F0" stroke-width="1"/>
        <circle cx="10" cy="10" r="4" fill="{accent}"/>
        <text x="20" y="14" font-family="'Inter', sans-serif" font-size="10.5" font-weight="600" fill="#475569">
            {esc_form}
        </text>
    </g>

    <!-- Realistic Dosage Form Artwork (Right Side) -->
    {form_art}

    <!-- Bottom Regulatory Bar -->
    <line x1="20" y1="214" x2="400" y2="214" stroke="#E2E8F0" stroke-width="1"/>

    <!-- Barcode Graphic Simulation (Left Bottom) -->
    <g transform="translate(24, 222)">
        <rect x="0" y="0" width="2" height="18" fill="#334155"/>
        <rect x="4" y="0" width="1" height="18" fill="#334155"/>
        <rect x="7" y="0" width="3" height="18" fill="#334155"/>
        <rect x="12" y="0" width="1" height="18" fill="#334155"/>
        <rect x="15" y="0" width="2" height="18" fill="#334155"/>
        <rect x="19" y="0" width="4" height="18" fill="#334155"/>
        <rect x="25" y="0" width="1" height="18" fill="#334155"/>
        <rect x="28" y="0" width="3" height="18" fill="#334155"/>
        <rect x="33" y="0" width="2" height="18" fill="#334155"/>
        <rect x="37" y="0" width="1" height="18" fill="#334155"/>
        <rect x="40" y="0" width="3" height="18" fill="#334155"/>
        <text x="48" y="14" font-family="'JetBrains Mono', monospace" font-size="9" fill="#94A3B8">{esc_eda}</text>
    </g>

    <!-- Manufacturer / Price Seal (Right Bottom) -->
    <g transform="translate(396, 235)">
        <text x="0" y="0" text-anchor="end" font-family="'Inter', sans-serif" font-size="9.5" font-weight="600" fill="#64748B">
            {esc_mfg[:28] + ('...' if len(esc_mfg) > 28 else '')} • <tspan fill="{primary}" font-weight="700">{price_tag}</tspan>
        </text>
    </g>

</svg>"""
    return svg
