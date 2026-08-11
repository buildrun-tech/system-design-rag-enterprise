---
version: alpha
name: OpenAI
description: "A light interface extracted from OpenAI accented with #8e8ea0, with a 8px spacing system and a system-ui type stack."
sourceUrl: "https://openai.com"

colors:
  primary: "#8e8ea0"
  on-primary: "#ffffff"
  text: "#8e8ea0"
  text-muted: "#000000"

typography:
  display:
    fontFamily: "system-ui, sans-serif"
    fontSize: 48px
    fontWeight: 700
    lineHeight: 1.5
  heading:
    fontFamily: "system-ui, sans-serif"
    fontSize: 32px
    fontWeight: 600
    lineHeight: 1.5
  body:
    fontFamily: "system-ui, sans-serif"
    fontSize: 16px
    fontWeight: 400
    lineHeight: 1.5

spacing:
  base: 8px
  scale: [8]

radius:
  sm: 5px

motion:
  duration-fast: 400ms
  duration-base: 400ms
  duration-slow: 400ms
  easing: "ease"

breakpoints: [768px]
---

## Rationale

OpenAI's design system reflects a product positioned at the intersection of advanced technology and human-centered interaction. The measured tokens reveal a deliberately restrained palette—a muted purple-gray primary (#8e8ea0) paired with stark black and white—that avoids the visual aggression typical of tech company branding. This choice suggests confidence: the interface doesn't need to shout. The typography stack defaults to system fonts (system-ui, sans-serif) rather than custom typefaces, prioritizing performance and immediate legibility over distinctive brand presence. Spacing and motion are minimal and consistent (8px base unit, uniform 400ms timing), creating a calm, predictable rhythm that supports focus during complex cognitive tasks like coding assistance. The absence of shadows and a single, modest border radius (5px) reinforces this minimalism—surfaces layer cleanly without depth effects that might add visual noise.

The color palette's restraint is strategic for an AI coding agent. The muted primary color and black text avoid eye fatigue during extended use, while the light color mode keeps the interface approachable rather than austere. There are no secondary colors or accent hues in the measured tokens, suggesting either a highly focused single-purpose interface or a deliberately pared-down measurement set. The on-primary white (used for text atop the muted purple) provides sufficient contrast for readability while maintaining the overall soft aesthetic.

Typography hierarchy is clear but not aggressive: a 3:2 ratio between heading (32px) and body (16px) scales comfortably, with generous 1.5 line height throughout supporting sustained reading. Display text (48px) is reserved for primary communication, but the consistent font weight progression (700 → 600 → 400) avoids jarring shifts. The system-ui default signals that this interface trusts the operating system's rendering—a choice that benefits developers and power users who expect native-like performance.

## 1. Visual Theme & Atmosphere

OpenAI's measured design expresses **calculated simplicity**. The absence of decorative shadows, gradients, or rounded corners beyond 5px creates a flat, engineered aesthetic that aligns with the precision expected from an AI coding tool. This is not minimalism born of constraint but of conviction: every element serves function. The light mode with muted purples and blacks suggests a workspace rather than a consumer app—professional, sustained-use focused, and visually non-intrusive.

The single breakpoint (768px) indicates a design system optimized for tablet and desktop workflows, with mobile likely treated as a secondary concern or responsive fallback. This reinforces positioning as a development-focused tool where large screens and complexity are assumed.

## 2. Color System

**Primary color:** #8e8ea0 (muted purple-gray)  
This is the dominant brand color, appearing in interactive elements and key affordances. It is distinctly desaturated—not a vibrant purple but a tone that sits between purple and gray, reducing chromatic stimulation over long sessions.

**On-primary:** #ffffff  
Text and icons placed atop primary backgrounds use pure white, creating sufficient contrast (estimated ~8:1) while maintaining visual softness.

**Text:** #8e8ea0  
Body text uses the same muted purple as primary elements, creating visual unity but *not* maximum contrast. This suggests a preference for cohesion over maximum accessibility (see Accessibility section below).

**Text-muted:** #000000  
Pure black is reserved for secondary or de-emphasized information, providing clear hierarchy through contrast intensity rather than color.

**No secondary colors** are measured, indicating a highly unified, monochromatic system. Accent colors or status indicators (success, error, warning) likely use saturation or value shifts within this limited palette.

## 3. Typography

The system uses four pre-defined scales:

- **Display (48px, weight 700, 1.5 line height):** Primary page headlines or hero messaging. Bold and spacious, designed for impact without aggression.
- **Heading (32px, weight 600, 1.5 line height):** Section titles and major content divisions. Slightly lighter weight than display but still commanding.
- **Body (16px, weight 400, 1.5 line height):** Standard reading text, interface labels, and code documentation. The 1.5 line height (24px total leading) is generous, supporting legibility during sustained reading and reducing eye fatigue.

All scales use system-ui, sans-serif, deferring to the user's operating system default. This choice prioritizes rendering fidelity and native feel over brand distinctiveness. For a coding tool, this is appropriate—developers expect interfaces to feel "of the system," not branded.

No monospace font is specified in the measured tokens, though any code display likely uses the browser/OS monospace default or a loaded font managed separately.

## 4. Components & Patterns

### Buttons and Interactive Elements
Interactive elements likely use the primary color (#8e8ea0) with white text. The 5px border radius is applied universally, creating a subtle softness without whimsy. No shadows suggest flat affordances with reliance on color and proximity to signal interactivity.

### Form Inputs
Likely use the 5px radius and muted color scheme. States (focus, hover, error) probably shift text-muted (#000000) or adjust opacity rather than introducing new colors.

### Cards and Containers
No shadows are specified, so content grouping relies on spacing (multiples of 8px), subtle borders, or background color shifts. This creates a clean, layered appearance without visual depth.

### Motion
All motion uses the same 400ms duration with ease timing, creating a predictable, non-jarring experience. This uniform approach suggests micro-interactions (hover states, focus indicators, transitions) are present but understated.

## 5. Spacing & Layout

The base unit is **8px**, with a single scale entry (8). This suggests a strict 8px grid system where all spacing is a multiple of 8 (8, 16, 24, 32, 40, 48, 56, 64px, etc.). 

This is a hallmark of professional product design: it ensures consistency, simplifies responsive scaling, and makes layout math trivial. A developer-focused tool would benefit immensely from this predictability.

The single breakpoint at **768px** likely represents the tablet/desktop threshold. Below 768px, the layout responds (possibly stacking sections, resizing typography proportionally). Above 768px, the full multi-column design is revealed. This suggests a mobile web presence but primary optimization for larger screens.

Generous line-height (1.5x across all type scales) and consistent 8px spacing units combine to create a spacious, breathing layout that feels less dense than many modern web products.

## 6. Motion & Interaction

All motion is standardized to **400ms (durationFastMs, durationBaseMs, durationSlowMs all equal 400ms)** with **ease** easing, creating a consistent, predictable feel.

This uniform timing is intentional: a coding interface should not surprise the user with inconsistent delays or jarring transitions. The 400ms duration is perceptible but not leisurely—fast enough to feel responsive, slow enough to be followed by the eye.

**Easing = ease** is the CSS default (cubic-bezier(0.25, 0.1, 0.25, 1)), which begins slowly, accelerates, and decelerates at the end. This is natural and non-fatiguing for repeated interactions.

No spring or bounce easing is present, reinforcing the serious, professional tone. Motion supports task completion, not delight.

---

## Accessibility

### Contrast Ratios

**Primary text (#8e8ea0) on a white background:** Estimated contrast ratio of ~3.5:1. This **fails WCAG AA** (4.5:1 minimum) and is borderline for WCAG AAA. This is a significant accessibility concern for a product intended for sustained reading and code review.

**Black text (#000000) on white background:** Contrast ratio of 21:1, exceeding all WCAG thresholds. Using black for critical or sensitive information mitigates the muted color issue.

**White text (#ffffff) on primary background (#8e8ea0):** Contrast ratio of ~8:1, exceeding all thresholds. Buttons and primary affordances are sufficiently accessible.

**Recommendation:** If large bodies of text appear in the muted purple (#8e8ea0), this should be audited and likely remapped to pure black (#000000) or darkened to improve contrast. The current palette favors aesthetic unity over strict accessibility compliance.

### Minimum Requirements

- **Touch target:** 44×44px minimum. With an 8px base unit, this equates to 5.5 base units—achievable via button padding (16px horizontal + 8px vertical padding = ~48px height) and reasonable minimum widths.
- **Focus indicator:** Should be a 2px outline (likely in the primary #8e8ea0 color or black for higher visibility) with 2px offset from the button edge. The 400ms ease transition should smoothly reveal focus states.
- **Keyboard navigation:** The system-ui font and flat, predictable spacing should support standard keyboard focus flows. No information should be color-only; interactive elements must have shape and text labels.

---

**Summary:** OpenAI's measured design is a masterclass in engineer-first aesthetics—minimal, consistent, performance-oriented. The trade-off is slightly reduced contrast in body text. For a coding-focused product, this balance is defensible; users of such tools are typically sighted and power-users. However, accessibility audits (especially automated contrast checks) should be performed before any public release.
