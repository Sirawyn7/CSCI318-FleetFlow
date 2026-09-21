package com.fleetflow.dispatch.application;

import com.fleetflow.dispatch.domain.GeoCoordinate;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.Map;

/** Local geographic configuration; unknown zones fail closed. No external maps API. */
@Component
@ConfigurationProperties(prefix = "dispatch")
public class OperatingZones {
    private Map<String, Bounds> zones = Map.of();
    public void setZones(Map<String, Bounds> zones) { this.zones = Map.copyOf(zones); }
    public Map<String, Bounds> getZones() { return zones; }

    public boolean serves(String zone, GeoCoordinate pickup) {
        Bounds bounds = zones.get(zone);
        if (bounds == null || !bounds.containsClosed(pickup)) return false;
        if (bounds.contains(pickup)) return true;
        // Prefer the half-open owner at shared edges. Where none exists, include
        // the closed perimeter. Zone ID breaks ties at shared perimeter corners
        // independently of map iteration order. Interior overlaps remain allowed.
        if (zones.values().stream().anyMatch(b -> b.contains(pickup))) return false;
        return zones.entrySet().stream().filter(e -> e.getValue().containsClosed(pickup))
                .map(Map.Entry::getKey).min(String::compareTo).filter(zone::equals).isPresent();
    }

    public record Bounds(double minLatitude, double maxLatitude, double minLongitude, double maxLongitude) {
        public Bounds {
            if (!Double.isFinite(minLatitude) || !Double.isFinite(maxLatitude)
                    || !Double.isFinite(minLongitude) || !Double.isFinite(maxLongitude)
                    || minLatitude < -90 || maxLatitude > 90 || minLongitude < -180 || maxLongitude > 180
                    || minLatitude >= maxLatitude || minLongitude >= maxLongitude) {
                throw new IllegalArgumentException("Zone bounds must be valid coordinates with positive area");
            }
        }
        public boolean contains(GeoCoordinate point) {
            // Half-open intervals give adjacent rectangles one owner for their shared edge.
            return point.latitude() >= minLatitude && point.latitude() < maxLatitude
                    && point.longitude() >= minLongitude && point.longitude() < maxLongitude;
        }

        private boolean containsClosed(GeoCoordinate point) {
            return point.latitude() >= minLatitude && point.latitude() <= maxLatitude
                    && point.longitude() >= minLongitude && point.longitude() <= maxLongitude;
        }
    }
}
