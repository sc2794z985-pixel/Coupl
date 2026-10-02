package com.auditlab.mod.render;

import com.auditlab.mod.analysis.model.ChunkAnalysis;
import com.auditlab.mod.analysis.model.ChunkScore;
import com.auditlab.mod.analysis.model.ScoreReason;

import java.util.ArrayList;
import java.util.List;

/** Builds the text lines shown above a chunk. Pure so it can be unit-tested. */
public final class LabelFormatter {
    /** Reason lines longer than this are truncated with an ellipsis. */
    public static final int MAX_LINE_CHARS = 48;

    private LabelFormatter() {
    }

    public static List<String> lines(ChunkAnalysis analysis, int reasonLines) {
        ChunkScore score = analysis.score();
        List<String> lines = new ArrayList<>();
        lines.add("Chunk " + analysis.key().x() + ", " + analysis.key().z()
            + "  Score " + score.total() + " (" + score.severity() + ")");
        for (ScoreReason r : score.topReasons(reasonLines)) lines.add(truncate(r.toString()));
        return lines;
    }

    static String truncate(String s) {
        return s.length() <= MAX_LINE_CHARS ? s : s.substring(0, MAX_LINE_CHARS - 1) + "…";
    }
}
