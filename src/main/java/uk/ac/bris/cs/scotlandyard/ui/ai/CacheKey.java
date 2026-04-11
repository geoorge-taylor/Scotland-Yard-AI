package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class CacheKey {
    final int mrX;
    final Set<Integer> detectives;

    CacheKey(int mrXLocation, Set<Integer> detectiveLocations) {
        this.detectives = new HashSet<>(detectiveLocations);
        this.mrX = mrXLocation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CacheKey)) return false;
        CacheKey other = (CacheKey) o;
        return mrX == other.mrX && detectives.equals(other.detectives);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mrX, detectives);
    }
}