import React, { useRef, useState } from 'react';
import {
  Dimensions,
  FlatList,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
  ViewToken,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { router } from 'expo-router';
import { colors, fontSize, fontWeight, radius, spacing } from '../constants/theme';

const { width } = Dimensions.get('window');

const SLIDES = [
  {
    id: '1',
    emoji: '💑',
    title: 'Für euch zwei',
    subtitle:
      'Coupl ist nur für euch. Verbindet euch mit eurem Partner und entdeckt gemeinsam, was ihr erleben möchtet.',
    bg: '#EDE7FF',
    dotColor: colors.primary,
  },
  {
    id: '2',
    emoji: '🃏',
    title: 'Swipt gemeinsam',
    subtitle:
      'Beide swipen Date-Ideen — Herz für "Ja", X für "Nein". Wenn ihr beide "Ja" sagt, entsteht ein Match!',
    bg: '#FFF8E6',
    dotColor: colors.accent,
  },
  {
    id: '3',
    emoji: '✨',
    title: 'Euer nächstes Date',
    subtitle:
      'Alle eure Matches landen in einer Liste. Kein Diskutieren mehr — die App entscheidet mit euch zusammen.',
    bg: '#E8FAF0',
    dotColor: '#6FCF97',
  },
];

export default function Onboarding() {
  const [activeIndex, setActiveIndex] = useState(0);
  const flatListRef = useRef<FlatList>(null);

  const onViewableItemsChanged = useRef(
    ({ viewableItems }: { viewableItems: ViewToken[] }) => {
      if (viewableItems.length > 0) {
        setActiveIndex(viewableItems[0].index ?? 0);
      }
    }
  ).current;

  const goNext = () => {
    if (activeIndex < SLIDES.length - 1) {
      flatListRef.current?.scrollToIndex({ index: activeIndex + 1 });
    } else {
      router.replace('/auth');
    }
  };

  const isLast = activeIndex === SLIDES.length - 1;

  return (
    <SafeAreaView style={styles.safe}>
      <FlatList
        ref={flatListRef}
        data={SLIDES}
        keyExtractor={(item) => item.id}
        horizontal
        pagingEnabled
        showsHorizontalScrollIndicator={false}
        onViewableItemsChanged={onViewableItemsChanged}
        viewabilityConfig={{ itemVisiblePercentThreshold: 50 }}
        renderItem={({ item }) => (
          <View style={[styles.slide, { width }]}>
            <View style={[styles.illustrationContainer, { backgroundColor: item.bg }]}>
              <Text style={styles.emoji}>{item.emoji}</Text>
            </View>
            <View style={styles.textBlock}>
              <Text style={styles.title}>{item.title}</Text>
              <Text style={styles.subtitle}>{item.subtitle}</Text>
            </View>
          </View>
        )}
      />

      <View style={styles.footer}>
        <View style={styles.dots}>
          {SLIDES.map((s, i) => (
            <View
              key={s.id}
              style={[
                styles.dot,
                {
                  backgroundColor:
                    i === activeIndex ? SLIDES[activeIndex].dotColor : colors.border,
                  width: i === activeIndex ? 24 : 8,
                },
              ]}
            />
          ))}
        </View>

        <TouchableOpacity
          style={[styles.button, isLast && styles.buttonPrimary]}
          onPress={goNext}
          activeOpacity={0.85}
        >
          <Text style={[styles.buttonText, isLast && styles.buttonTextPrimary]}>
            {isLast ? 'Jetzt starten' : 'Weiter'}
          </Text>
        </TouchableOpacity>

        {!isLast && (
          <TouchableOpacity onPress={() => router.replace('/auth')} style={styles.skip}>
            <Text style={styles.skipText}>Überspringen</Text>
          </TouchableOpacity>
        )}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: {
    flex: 1,
    backgroundColor: colors.background,
  },
  slide: {
    flex: 1,
    alignItems: 'center',
  },
  illustrationContainer: {
    width: '100%',
    height: 380,
    alignItems: 'center',
    justifyContent: 'center',
    borderBottomLeftRadius: radius.xl,
    borderBottomRightRadius: radius.xl,
  },
  emoji: {
    fontSize: 100,
  },
  textBlock: {
    paddingHorizontal: spacing.xl,
    paddingTop: spacing.xl,
    alignItems: 'center',
  },
  title: {
    fontSize: fontSize.xxl,
    fontWeight: fontWeight.bold,
    color: colors.text,
    textAlign: 'center',
    marginBottom: spacing.md,
  },
  subtitle: {
    fontSize: fontSize.md,
    color: colors.textSecondary,
    textAlign: 'center',
    lineHeight: 24,
  },
  footer: {
    paddingHorizontal: spacing.xl,
    paddingBottom: spacing.xl,
    alignItems: 'center',
    gap: spacing.md,
  },
  dots: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.xs,
    marginBottom: spacing.sm,
  },
  dot: {
    height: 8,
    borderRadius: radius.full,
  },
  button: {
    width: '100%',
    paddingVertical: spacing.md + 2,
    borderRadius: radius.full,
    alignItems: 'center',
    backgroundColor: colors.border,
  },
  buttonPrimary: {
    backgroundColor: colors.primary,
  },
  buttonText: {
    fontSize: fontSize.md,
    fontWeight: fontWeight.semibold,
    color: colors.textSecondary,
  },
  buttonTextPrimary: {
    color: colors.text,
  },
  skip: {
    paddingVertical: spacing.sm,
  },
  skipText: {
    fontSize: fontSize.sm,
    color: colors.textLight,
  },
});
