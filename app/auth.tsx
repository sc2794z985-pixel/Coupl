import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { colors, fontSize, fontWeight, spacing } from '../constants/theme';

export default function Auth() {
  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.center}>
        <Text style={styles.logo}>Coupl</Text>
        <Text style={styles.sub}>Login — coming soon</Text>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: spacing.md },
  logo: { fontSize: fontSize.xxxl, fontWeight: fontWeight.extrabold, color: colors.text },
  sub: { fontSize: fontSize.md, color: colors.textSecondary },
});
