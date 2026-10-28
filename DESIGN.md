---
name: Clinical Liquid Glass
colors:
  surface: '#effcf9'
  surface-dim: '#cfddd9'
  surface-bright: '#effcf9'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#e9f7f3'
  surface-container: '#e3f1ed'
  surface-container-high: '#ddebe7'
  surface-container-highest: '#d8e5e2'
  on-surface: '#121e1c'
  on-surface-variant: '#3e4947'
  inverse-surface: '#273331'
  inverse-on-surface: '#e6f4f0'
  outline: '#6e7977'
  outline-variant: '#bdc9c6'
  surface-tint: '#006a63'
  primary: '#005c55'
  on-primary: '#ffffff'
  primary-container: '#0f766e'
  on-primary-container: '#a3faef'
  inverse-primary: '#80d5cb'
  secondary: '#216963'
  on-secondary: '#ffffff'
  secondary-container: '#a8ece5'
  on-secondary-container: '#266d68'
  tertiary: '#005d3f'
  on-tertiary: '#ffffff'
  tertiary-container: '#1f7754'
  on-tertiary-container: '#a7fbd0'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#9cf2e8'
  primary-fixed-dim: '#80d5cb'
  on-primary-fixed: '#00201d'
  on-primary-fixed-variant: '#00504a'
  secondary-fixed: '#abefe8'
  secondary-fixed-dim: '#8fd3cc'
  on-secondary-fixed: '#00201e'
  on-secondary-fixed-variant: '#00504b'
  tertiary-fixed: '#a0f4c9'
  tertiary-fixed-dim: '#84d7ae'
  on-tertiary-fixed: '#002113'
  on-tertiary-fixed-variant: '#005236'
  background: '#effcf9'
  on-background: '#121e1c'
  surface-variant: '#d8e5e2'
  background-canvas: '#F7F8F6'
  surface-crisp: '#FFFFFF'
  glass-tint: rgba(255, 255, 255, 0.78)
  glass-fallback: '#EFF2EF'
  glass-border: rgba(255, 255, 255, 0.45)
  text-primary: '#172321'
  text-secondary: '#52615E'
  border-subtle: '#DCE5E1'
  priority-urgent: '#B42318'
  priority-warning: '#8A5200'
  priority-upcoming: '#0F766E'
  status-success: '#237A57'
typography:
  display-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 26px
    fontWeight: '700'
    lineHeight: 34px
    letterSpacing: -0.015em
  headline-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 22px
    fontWeight: '600'
    lineHeight: 28px
    letterSpacing: -0.01em
  headline-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
  title-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 22px
  body-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 13px
    fontWeight: '400'
    lineHeight: 18px
  label-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 18px
  label-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.02em
  label-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 11px
    fontWeight: '500'
    lineHeight: 14px
    letterSpacing: 0.03em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  margin: 1rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2rem
---

## Brand & Style

This design system delivers a calm, authoritative, and tactically resilient mobile experience engineered specifically for frontline ASHA and ANM healthcare workers operating in diverse and low-connectivity environments. The aesthetic marries the spatial depth, refraction, and optical clarity of next-generation translucent glass surfaces with rigorous clinical utility and extreme daylight legibility.

### Emotional Disposition & Target Audience
- **Target Audience:** Field health workers, community nurses, and auxiliary midwives balancing home visits, protocol consultations, and immunization tracking on Android devices under harsh outdoor lighting.
- **Atmosphere:** Deeply reassuring, composed, clinically precise, and tactile without visual noise. It projects institutional trustworthiness and modern technological capability without sacrificing field ergonomics.
- **Design Philosophy:** Grounded Glassmorphic Minimalism. Heavy, opaque content panels rest on a warm, glare-reducing canvas, while spatial translucency and optical refraction are applied selectively to persistent navigation layers and floating contextual overlays.

## Colors

The color palette centers on clinical discipline and outdoor visibility, rejecting synthetic neon accents in favor of mineral pigments and accessible chromatic ratios.

### Key Palettes & Hierarchy
- **Primary (`#0F766E`):** Deep marine teal used for authoritative interactive states, selected navigation targets, primary CTAs, and routine upcoming schedules.
- **Secondary (`#115E59`):** Deep sea teal utilized for elevated interactive contrast, focused outlines, and dense UI header elements.
- **Tertiary & Success (`#237A57`):** Earth sage green reserved strictly for confirmed actions, recorded visits, and synchronized offline states.
- **Neutral (`#172321`):** Deep charcoal ink delivering a minimum 7:1 contrast ratio against light card surfaces, avoiding eye strain associated with pure digital black.
- **Canvas & Surface:** Grounded in a natural warm off-white (`#F7F8F6`) canvas to curb outdoor solar reflection, layered with crisp white (`#FFFFFF`) solid cards and a high-transmissivity translucent glass matrix (`rgba(255, 255, 255, 0.78)`).

### Clinical Status & Field Urgency
- **Urgent Danger (`#B42318`):** High-priority alerts and maternal/infant critical follow-ups. Must always be accompanied by descriptive text and icon badges.
- **Due-Soon Attention (`#8A5200`):** Routine schedule thresholds, due immunizations, and time-sensitive reminders.
- **Subtle Boundaries (`#DCE5E1`):** Muted structural hairpins to define card envelopes without clutter.

## Typography

Typography is set entirely in **Plus Jakarta Sans**, chosen for its humane geometric balance, open counters, clear aperture distinctions, and superior rendering fidelity on varied Android density screens.

### Field Ergonomics & Rules
- **No Decorative Weights:** Thin or light weights below 400 are prohibited to prevent stroke dissolution in direct sunlight.
- **Strict Hierarchy:** Numbers and metrics (e.g., visit counts) use 700 bold weight for instantaneous glanceability during motion.
- **Dynamic Type Adaptability:** Every typography token adheres to proportionate line-height scaling to comfortably accommodate Android system font magnification without clipping or horizontal text collision.
- **Caps Discipline:** Labels never use fully capitalized letter strings exceeding 10 characters to preserve rapid reading velocity for emergency protocols.

## Layout & Spacing

Layout geometry follows an uncompromising 4dp (0.25rem) mathematical baseline grid tuned for mobile viewports (targeting baseline 393 × 852 logical dp while scaling seamlessly down to 360dp widths).

### Canvas Architecture & Safe Margins
- **Outer Shell Margins:** A fixed `16dp` (`1rem`) lateral screen margin ensures edge tap safety on curved glass Android bezels.
- **Touch Target Threshold:** All tap interactive components enforce a non-negotiable minimum physical envelope of `48 × 48dp`.
- **Bottom Clearance Inset:** Because the primary floating glass capsule rests over the bottom viewport, scroll containers must implement a mandatory bottom padding clearance of `96dp` (`6rem`). Content smoothly blurs underneath during motion but comes to a complete rest safely above the glass barrier.
- **Vertical Rhythm:** 
  - `space-xs` (4dp) separates paired micro-labels and status indicators.
  - `space-sm` (8dp) governs intra-component padding and inline pill spacing.
  - `space-md` (16dp) dictates structural card padding and standard vertical gaps between list items.
  - `space-lg` (24dp) structures intra-section hierarchy on primary field dashboards.

## Elevation & Depth

Visual hierarchy uses physical spatial layering combined with optical refraction, separating interactive navigation from static data containers without muddying the interface.

### The Liquid Glass Matrix (Navigation & Persistent Overlays)
- **Optical Treatment:** Floating glass elements use a dual-layer surface: `rgba(255, 255, 255, 0.78)` fill backed by an Android Jetpack Compose `RenderEffect.createBlurEffect(16dp, 16dp)`.
- **Rim Highlight:** A 1dp inner boundary gradient using `rgba(255, 255, 255, 0.65)` across top and lateral edges, fading to `rgba(220, 229, 225, 0.3)` at the bottom, creating realistic directional refraction.
- **Physical Drop Shadow:** Subtle ambient diffusion: `0 12px 32px -4px rgba(23, 35, 33, 0.08)`, paired with a direct contact shadow of `0 4px 12px -2px rgba(23, 35, 33, 0.04)`.
- **Reduced Transparency Fallback:** On lower-tier hardware where background blur is throttled, the layer gracefully falls back to an opaque `#EFF2EF` surface paired with a discrete `#DCE5E1` boundary stroke and ambient drop shadow.

### Content Containers & Cards
- **Layer 0 (Canvas):** `#F7F8F6` matte background.
- **Layer 1 (Standard Card Surfaces):** `#FFFFFF` crisp solid fill, zero shadow, framed with a 1dp crisp boundary of `#DCE5E1`. This ensures 100% text clarity and zero battery/GPU strain during heavy list scrolling.
- **Layer 2 (Floating Action Modules & Modals):** Crisp `#FFFFFF` surface lifted with `0 8px 24px -2px rgba(23, 35, 33, 0.08)`.

## Shapes

The design system maintains a balanced, organic curve profile that visually echoes natural ergonomics while keeping content bounding boxes structured and clean.

### Corner Radii Architecture
- **Capsule Shapes (`rounded-full` / 9999px):** Exclusively reserved for the floating bottom navigation bar, quick status pills, priority alert badges, and primary interactive button pills.
- **Cards & Data Modules (`rounded-xl` / 16dp / 1.5rem):** Standardized on 16dp corner radiuses, yielding soft, modern containment that distinguishes patient visit blocks from each other.
- **Text Inputs & Filter Tabs (`rounded-lg` / 12dp / 1rem):** Balanced tactile containers designed for rapid finger placement.
- **Internal Selection Chips (`rounded` / 8dp / 0.5rem):** Modest internal corner curvature matching the parent boundary curvature.

## Components

### Floating Liquid Glass Bottom Navigation
The flagship navigation module is a floating capsule (height: 64dp, margin: 16dp horizontal, elevated 16dp above the Android gesture bar).
- **Structure:** Exactly four destinations arranged horizontally:
  1. **Today** (`calendar-check` icon)
  2. **Families** (`users` icon)
  3. **Assistant** (`message-circle` icon)
  4. **Records** (`clipboard-list` icon)
- **Active State:** The selected tab receives an internal pill treatment tinted with `rgba(15, 118, 110, 0.12)`, holding the primary teal icon (`#0F766E`) and bold label.
- **Inactive State:** Slate gray icon (`#52615E`) paired with medium label weight; minimum touch target spans equal fourths of the bar width.
- **Feedback:** Subtle scale reaction (`0.96`) on tap, without delayed animations.

### Patient & Field Visit Cards
- **Structure:** Crisp white background (`#FFFFFF`), 16dp rounded corners, 1dp border (`#DCE5E1`), 16dp padding.
- **Header:** Household ID (e.g., `HH-014`) displayed in compact bold monospace or neutral sans, flanked on the right by a priority indicator pill.
- **Priority Badge Tokens:**
  - *High Priority:* Tint `rgba(180, 35, 24, 0.1)`, Text/Border `#B42318`.
  - *Due Today:* Tint `rgba(138, 82, 0, 0.1)`, Text/Border `#8A5200`.
  - *Upcoming:* Tint `rgba(15, 118, 110, 0.1)`, Text/Border `#0F766E`.
- **Action:** Full card is tap-responsive, ending in a discrete arrow glyph leading into household files.

### Metrics & Daily Tally Counter
- **Grid Layout:** 3-column equal split.
- **Appearance:** Crisp white background, 12dp rounded corners, subtle border, center-aligned.
- **Hierarchy:** Number metric set at `display-lg` in `#172321`, accompanied below by a concise two-word description in `label-sm` slate text (`#52615E`).

### Buttons
- **Primary:** Solid `#0F766E` pill, white bold text, min-height 48dp, elevation zero, active press color `#115E59`.
- **Secondary / Protocol:** Crisp white surface with `#DCE5E1` border, text `#0F766E`.
- **Urgent / Danger Action:** Solid `#B42318` pill with high-contrast white text.

### Protocol Assistant Q&A Panels
- **Prompt Field:** Bottom-docked input bar elevated above the navigation clearance, featuring an enclosed pill container with an inline forward/send button.
- **Citation Badges:** Citations embedded in verified protocol answers use small pill badges with a book/reference icon, styled with a `#EFF2EF` tint and `#172321` label text to emphasize clinical provenance.