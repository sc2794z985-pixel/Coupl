export const colors = {
  primary: '#C9B8FF',       // Soft Lavender
  primaryDark: '#9B85F5',   // Deeper Lavender
  accent: '#FFD166',        // Warm Yellow
  accentDark: '#F5B800',    // Deeper Yellow
  background: '#FAFAF8',    // Off-White
  surface: '#FFFFFF',
  text: '#1A1A2E',          // Deep Navy
  textSecondary: '#6B6B8A', // Muted
  textLight: '#A0A0B8',
  border: '#EAE8F0',
  success: '#6FCF97',
  error: '#EB5757',
  matchGradientStart: '#C9B8FF',
  matchGradientEnd: '#FFD166',
} as const;

export const spacing = {
  xs: 4,
  sm: 8,
  md: 16,
  lg: 24,
  xl: 32,
  xxl: 48,
  xxxl: 64,
} as const;

export const radius = {
  sm: 8,
  md: 16,
  lg: 24,
  xl: 32,
  full: 999,
} as const;

export const fontSize = {
  xs: 12,
  sm: 14,
  md: 16,
  lg: 20,
  xl: 24,
  xxl: 32,
  xxxl: 42,
} as const;

export const fontWeight = {
  regular: '400' as const,
  medium: '500' as const,
  semibold: '600' as const,
  bold: '700' as const,
  extrabold: '800' as const,
};
