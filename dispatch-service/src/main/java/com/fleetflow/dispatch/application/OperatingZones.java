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
        return bounds != null && bounds.contains(pickup);
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
            return point.latitude() >= minLatitude && point.latitude() <= maxLatitude
                    && point.longitude() >= minLongitude && point.longitude() <= maxLongitude;
        }
    }
}
