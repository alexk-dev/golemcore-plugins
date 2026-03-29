package me.golemcore.plugins.golemcore.obsidian.model;

import java.util.List;

public final class ObsidianSearchResult {

    private final String filename;
    private final double score;
    private final List<Match> matches;

    public ObsidianSearchResult(String filename, double score, List<Match> matches) {
        this.filename = filename;
        this.score = score;
        this.matches = matches == null ? List.of() : List.copyOf(matches);
    }

    public String getFilename() {
        return filename;
    }

    public double getScore() {
        return score;
    }

    public List<Match> getMatches() {
        return matches;
    }

    public static final class Match {

        private final String context;
        private final MatchSpan match;

        public Match(String context, MatchSpan match) {
            this.context = context;
            this.match = match;
        }

        public String getContext() {
            return context;
        }

        public MatchSpan getMatch() {
            return match;
        }
    }

    public static final class MatchSpan {

        private final Integer start;
        private final Integer end;
        private final String source;

        public MatchSpan(Integer start, Integer end, String source) {
            this.start = start;
            this.end = end;
            this.source = source;
        }

        public Integer getStart() {
            return start;
        }

        public Integer getEnd() {
            return end;
        }

        public String getSource() {
            return source;
        }
    }
}
